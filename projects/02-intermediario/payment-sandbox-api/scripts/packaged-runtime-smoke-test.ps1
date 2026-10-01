[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot "compose.yaml"
$envFile = Join-Path $projectRoot ".env"
$composeProject = "payment-sandbox-smoke"
$apiBaseUrl = "http://localhost:8080"
$keycloakBaseUrl = "http://localhost:8180"

function Write-Pass {
    param([Parameter(Mandatory)][string] $Message)

    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Convert-HttpContentToText {
    param([Parameter(Mandatory)] $Content)

    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }

    return [string] $Content
}

function Read-DotEnv {
    param([Parameter(Mandatory)][string] $Path)

    $values = @{}

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separator = $trimmed.IndexOf("=")
        if ($separator -le 0) {
            continue
        }

        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()

        if ($value.Length -ge 2) {
            $quotedWithDoubleQuotes = $value.StartsWith('"') -and $value.EndsWith('"')
            $quotedWithSingleQuotes = $value.StartsWith("'") -and $value.EndsWith("'")
            if ($quotedWithDoubleQuotes -or $quotedWithSingleQuotes) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        $values[$key] = $value
    }

    return $values
}

function Invoke-Compose {
    param([Parameter(Mandatory)][string[]] $Arguments)

    & docker compose `
        --project-name $composeProject `
        --env-file $envFile `
        --file $composeFile `
        @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE."
    }
}

function Wait-ForHttpStatus {
    param(
        [Parameter(Mandatory)][string] $Uri,
        [Parameter(Mandatory)][int] $ExpectedStatus,
        [int] $TimeoutSeconds = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    do {
        try {
            $response = Invoke-WebRequest `
                -Uri $Uri `
                -Method Get `
                -UseBasicParsing `
                -ErrorAction Stop `
                -TimeoutSec 5

            if ([int] $response.StatusCode -eq $ExpectedStatus) {
                return $response
            }
        } catch {
            # The service can refuse connections while its process is starting.
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for HTTP $ExpectedStatus from $Uri."
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Run this script from a host with Docker Desktop available."
}

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Create it from .env.example before running the smoke test."
}

$environment = Read-DotEnv -Path $envFile
$merchantASecret = $environment["MERCHANT_A_CLIENT_SECRET"]

if ([string]::IsNullOrWhiteSpace($merchantASecret)) {
    throw "MERCHANT_A_CLIENT_SECRET is missing from .env."
}

$smokeFailure = $null

try {
    Write-Host "Starting isolated Compose project '$composeProject'..."
    Invoke-Compose -Arguments @("up", "--detach", "--build")

    Wait-ForHttpStatus `
        -Uri "$keycloakBaseUrl/realms/payment-sandbox/.well-known/openid-configuration" `
        -ExpectedStatus 200 | Out-Null
    Write-Pass "Keycloak discovery endpoint is available"

    $readinessResponse = Wait-ForHttpStatus `
        -Uri "$apiBaseUrl/actuator/health/readiness" `
        -ExpectedStatus 200
    $readinessContent = Convert-HttpContentToText -Content $readinessResponse.Content
    $readiness = ConvertFrom-Json -InputObject $readinessContent
    $readinessStatusProperty = $readiness.PSObject.Properties["status"]
    if ($null -eq $readinessStatusProperty) {
        throw "API readiness response does not contain 'status'. Body: $readinessContent"
    }
    $readinessStatus = [string] $readinessStatusProperty.Value
    if ($readinessStatus -ne "UP") {
        throw "API readiness returned '$readinessStatus' instead of 'UP'."
    }
    Write-Pass "API readiness is UP"

    $unauthenticatedStatus = $null
    try {
        $unauthenticatedResponse = Invoke-WebRequest `
            -Uri "$apiBaseUrl/api/v1/payments" `
            -Method Get `
            -UseBasicParsing `
            -ErrorAction Stop
        $unauthenticatedStatus = [int] $unauthenticatedResponse.StatusCode
    } catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }
        $unauthenticatedStatus = [int] $_.Exception.Response.StatusCode
    }
    if ($unauthenticatedStatus -ne 401) {
        throw "Protected route returned HTTP $unauthenticatedStatus without a token; expected 401."
    }
    Write-Pass "Protected route returns 401 without a token"

    $tokenResponse = Invoke-RestMethod `
        -Uri "$keycloakBaseUrl/realms/payment-sandbox/protocol/openid-connect/token" `
        -Method Post `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            grant_type = "client_credentials"
            client_id = "merchant-a-client"
            client_secret = $merchantASecret
        }

    if ([string]::IsNullOrWhiteSpace($tokenResponse.access_token)) {
        throw "Keycloak response did not contain an access token."
    }
    Write-Pass "Keycloak issued a client credentials token"

    $runId = (New-Guid).ToString("N")
    $requestBody = @{
        amount = 10000
        currency = "BRL"
        merchantReference = "SMOKE-$runId"
        paymentMethodToken = "tok_approved"
    } | ConvertTo-Json

    $authenticatedHeaders = @{
        Authorization = "Bearer $($tokenResponse.access_token)"
        "Idempotency-Key" = "smoke-$runId"
    }

    $creationResponse = Invoke-WebRequest `
        -Uri "$apiBaseUrl/api/v1/payments" `
        -Method Post `
        -Headers $authenticatedHeaders `
        -ContentType "application/json" `
        -Body $requestBody `
        -UseBasicParsing `
        -ErrorAction Stop

    if ([int] $creationResponse.StatusCode -ne 201) {
        throw "Payment creation returned HTTP $($creationResponse.StatusCode); expected 201."
    }

    $creationContent = Convert-HttpContentToText -Content $creationResponse.Content
    $createdPayment = ConvertFrom-Json -InputObject $creationContent
    if ([string]::IsNullOrWhiteSpace($createdPayment.id) -or $createdPayment.status -ne "APPROVED") {
        throw "Payment creation did not return an approved payment with an ID."
    }
    Write-Pass "Payment creation returns 201 with APPROVED status"

    $queryResponse = Invoke-WebRequest `
        -Uri "$apiBaseUrl/api/v1/payments/$($createdPayment.id)" `
        -Method Get `
        -Headers @{ Authorization = "Bearer $($tokenResponse.access_token)" } `
        -UseBasicParsing `
        -ErrorAction Stop

    if ([int] $queryResponse.StatusCode -ne 200) {
        throw "Payment query returned HTTP $($queryResponse.StatusCode); expected 200."
    }

    $queryContent = Convert-HttpContentToText -Content $queryResponse.Content
    $queriedPayment = ConvertFrom-Json -InputObject $queryContent
    if ($queriedPayment.id -ne $createdPayment.id -or $queriedPayment.status -ne "APPROVED") {
        throw "Payment query did not return the payment created by the smoke test."
    }
    Write-Pass "Payment query returns the created payment"

    $apiContainerIdOutput = & docker compose `
        --project-name $composeProject `
        --env-file $envFile `
        --file $composeFile `
        ps --quiet api
    if ($LASTEXITCODE -ne 0) {
        throw "Could not resolve the API container ID."
    }

    $apiContainerId = (($apiContainerIdOutput | Out-String).Trim())
    if ([string]::IsNullOrWhiteSpace($apiContainerId)) {
        throw "Compose did not return an API container ID."
    }

    $containerUser = ((& docker inspect --format '{{.Config.User}}' $apiContainerId) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerUser) -or $containerUser -in @("0", "root")) {
        throw "API container is not configured with a non-root user."
    }
    Write-Pass "API container runs as non-root user '$containerUser'"

    $readOnlyRoot = ((& docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' $apiContainerId) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $readOnlyRoot -ne "true") {
        throw "API container root filesystem is not read-only."
    }
    Write-Pass "API container root filesystem is read-only"

    $droppedCapabilities = ((& docker inspect --format '{{json .HostConfig.CapDrop}}' $apiContainerId) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $droppedCapabilities -notmatch 'ALL') {
        throw "API container did not drop all Linux capabilities."
    }
    Write-Pass "API container drops all Linux capabilities"

    $securityOptions = ((& docker inspect --format '{{json .HostConfig.SecurityOpt}}' $apiContainerId) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $securityOptions -notmatch 'no-new-privileges:true') {
        throw "API container does not enforce no-new-privileges."
    }
    Write-Pass "API container enforces no-new-privileges"

    $imageLabelsJson = ((& docker image inspect payment-sandbox-api:local --format '{{json .Config.Labels}}') | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the API image labels."
    }

    $imageLabels = ConvertFrom-Json -InputObject $imageLabelsJson
    $sourceLabelProperty = $imageLabels.PSObject.Properties["org.opencontainers.image.source"]
    if ($null -eq $sourceLabelProperty -or $sourceLabelProperty.Value -ne "https://github.com/Tawfik-Metwally/web-projects") {
        throw "API image does not contain the expected OCI source label."
    }
    Write-Pass "API image contains the expected OCI source label"
} catch {
    $smokeFailure = $_
} finally {
    Write-Host "Removing isolated smoke environment and its temporary volumes..."
    & docker compose `
        --project-name $composeProject `
        --env-file $envFile `
        --file $composeFile `
        down --volumes --remove-orphans

    if ($LASTEXITCODE -ne 0 -and $null -eq $smokeFailure) {
        $smokeFailure = "Smoke checks passed, but cleanup failed with exit code $LASTEXITCODE."
    } elseif ($LASTEXITCODE -eq 0) {
        Write-Pass "Smoke environment and temporary volumes were removed"
    }
}

if ($null -ne $smokeFailure) {
    throw $smokeFailure
}

Write-Host "Packaged runtime smoke test passed." -ForegroundColor Green
