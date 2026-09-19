# Local testing guide

This guide runs the Payment Sandbox API with real Keycloak tokens and the persistent
`payments_demo` database. All payments are simulated; never use real card data.

The API, Keycloak, and PostgreSQL are different services. Request a token from
Keycloak, then send that token to the API. DBeaver connects directly to PostgreSQL
for local inspection; it does not authenticate through the API.

## Contents

- [Prepare and start the environment](#prepare-and-start-the-environment)
- [Addresses and credentials](#addresses-and-credentials)
- [Obtain tokens manually](#obtain-tokens-manually)
- [Use Postman's OAuth 2.0 helper](#use-postmans-oauth-20-helper)
- [Use OpenAPI, Swagger UI, and Postman](#use-openapi-swagger-ui-and-postman)
- [Inspect health and protected metrics](#inspect-health-and-protected-metrics)
- [Run the payment and security scenario](#run-the-payment-and-security-scenario)
- [Trace correlation and safe logs](#trace-correlation-and-safe-logs)
- [Inspect persistence with DBeaver](#inspect-persistence-with-dbeaver)
- [Run automated tests](#run-automated-tests)
- [Troubleshooting](#troubleshooting)
- [Stop without deleting data](#stop-without-deleting-data)

## Prepare and start the environment

Requirements: Docker Desktop with Linux containers, VS Code with Dev Containers,
Postman Desktop, and optionally DBeaver.

### First-time configuration

Open the project folder containing `pom.xml`, `compose.yaml`, and `mvnw`,
not the parent repository. If `.env` does not exist, create it from
[`.env.example`](../.env.example). In a **Windows host PowerShell terminal**:

```powershell
Copy-Item .env.example .env
```

Do not overwrite an existing `.env`. Replace all placeholder passwords before
starting containers. Set `DEMO_DB_PASSWORD` and independent random values for
`MERCHANT_A_CLIENT_SECRET`, `MERCHANT_B_CLIENT_SECRET`, and
`OPERATIONS_CLIENT_SECRET`. Keep database names consistent with the template
unless you intentionally redesign the setup.

Do not commit secrets, tokens, screenshots containing credentials, or populated
Postman exports. These local HTTP addresses are for development only; deployed
credentials and tokens require HTTPS.

In VS Code, run **Dev Containers: Reopen in Container**. VS Code starts the
`dev`, `postgres`, and `keycloak` services. The packaged `api` service is
excluded from this workflow. Commands below marked **Dev Container Bash** run in
`/workspace`.

On a fresh PostgreSQL volume, initialization creates the demo database when
`DEMO_DB_PASSWORD` is set. If your existing environment already has a working
`payments_demo` connection, skip the next subsection.

### Existing environment without the demo database

If you added `DEMO_DB_PASSWORD` after the first startup:

1. Stop Spring Boot with **Ctrl+C**.
2. In a **host terminal**, from this project folder, run:

   ```powershell
   docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml up -d postgres
   ```

3. Run **Dev Containers: Rebuild Container** in VS Code to load the environment.
4. In **Dev Container Bash**, run:

   ```bash
   docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml exec postgres sh /opt/payment-sandbox/init-demo-database.sh
   ```

This initializes a missing demo role/database without deleting existing records.
It does not reset existing passwords. Never delete the shared database volume
to fix setup. Flyway creates application tables when the API starts.

### Existing environment without the operations client

Keycloak imports the versioned realm automatically only when the realm does not
exist. If the local `payment-sandbox` realm predates the operations client, first
add a private `OPERATIONS_CLIENT_SECRET` value to `.env`. Stop Spring Boot, then
run in a **Windows host PowerShell terminal** from the project directory. The
host terminal is required because this one-off container bind-mounts the realm
file from the Windows workspace:

```powershell
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml stop keycloak
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml run --rm --no-deps keycloak import --file /opt/keycloak/data/import/payment-sandbox-realm.json --override true
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml up -d --no-deps keycloak
```

The offline import replaces only the `payment-sandbox` realm configuration. It
does not delete the `payments` or `payments_demo` databases. Existing merchant
client secrets are read again from the same `.env` file. Wait for Keycloak to
start before requesting new tokens.

### Start the API

In **Dev Container Bash**:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Wait for application startup and keep this terminal open. Ensure VS Code forwards
port 8080 to `localhost:8080`. Stop an existing application before starting
another instance.

The `demo` profile selects the database only. JWT authentication and scope checks
remain enabled. The old `demo-no-auth` profile has been removed.

To use the development database instead, stop the application and run
`./mvnw spring-boot:run` without the profile. Do not set `SPRING_DATASOURCE_*`
overrides in the Dev Container when using the demo profile.

In Postman, select **No Auth** and send:

```http
GET http://localhost:8080/actuator/health
```

Expected: `200 OK` and `{"status":"UP"}`.

## Addresses and credentials

| Purpose | Address or value |
|---|---|
| API base URL | `http://localhost:8080` |
| Keycloak administration | `http://localhost:8180` |
| Token endpoint | `http://localhost:8180/realms/payment-sandbox/protocol/openid-connect/token` |
| Expected token issuer | `http://localhost:8180/realms/payment-sandbox` |
| Expected token audience | `payment-sandbox-api` |
| JWK Set used inside Docker | `http://keycloak:8080/realms/payment-sandbox/protocol/openid-connect/certs` |
| DBeaver host / port | `localhost` / `5432` |
| Demo database / username | `payments_demo` / `payments_demo` |
| Demo database password | Your private `DEMO_DB_PASSWORD` value |

The host uses published ports 8180 and 5432; containers use service names such
as `keycloak:8080` and `postgres:5432`. Do not replace the Postman token URL
with the Docker-only hostname.

| Identity | Client ID | Secret source in local `.env` | Default scopes |
|---|---|---|---|
| A | `merchant-a-client` | `MERCHANT_A_CLIENT_SECRET` | `payments:create payments:read refunds:create` |
| B | `merchant-b-client` | `MERCHANT_B_CLIENT_SECRET` | `payments:create payments:read` |
| Operations | `operations-client` | `OPERATIONS_CLIENT_SECRET` | `observability:read` |

Client IDs are public identifiers; secrets are private credentials. A client
secret is not Keycloak's private token-signing key.

The realm is imported from [its versioned configuration](../docker/keycloak/payment-sandbox-realm.json)
on first startup. An existing realm is preserved. Editing `.env` or the import
JSON alone does not update an existing realm or rotate its secrets.

## Obtain tokens manually

Create a Postman request with:

- Method: **POST**
- URL: `http://localhost:8180/realms/payment-sandbox/protocol/openid-connect/token`
- Authorization: **No Auth**
- Body: **x-www-form-urlencoded**

For merchant A, enter these enabled rows:

| Key | Value |
|---|---|
| `grant_type` | `client_credentials` |
| `client_id` | `merchant-a-client` |
| `client_secret` | Your actual merchant A secret, without surrounding quotes |

Postman supplies the form Content-Type. Do not manually retain a JSON Content-Type
header. `grant_type` selects the OAuth flow; the client ID and secret authenticate
the merchant system. These fields go to Keycloak, not to an API controller.

Send the request. Expected: `200 OK`, an `access_token`, `token_type: Bearer`,
and `expires_in: 300` (seconds). Scope order may vary. A must have all three
business scopes; B must not have `refunds:create`.

Repeat in a separate tab for B, using its client ID and matching secret.
Keep the token requests available for obtaining new tokens.

Create a third token request for `operations-client`, using the corresponding
secret. Its response scope must contain `observability:read` and none of the
three business scopes.

The default scopes are already linked to each client. Leave the optional
`scope` field absent. Requesting a scope does not grant a new permission.

For API requests, choose **Authorization > Bearer Token** and paste only the
`access_token` value, without JSON quotes or the word `Bearer`. Postman sends:

```http
Authorization: Bearer <access_token>
```

Remove duplicate manually entered Authorization headers and old
`X-Demo-Merchant-Id` / `X-Merchant-Id` headers. Merchant identity comes from the
validated token's `azp`, not from a request header, query parameter, or body field.

## Use Postman's OAuth 2.0 helper

This is an alternative to requesting and copying tokens manually, not a different
API security mechanism. On an **API request**, open **Authorization > OAuth 2.0**.
Choose to add authentication to the request headers, with prefix `Bearer`.

Under **Configure New Token**, fill in:

| Field | Merchant A | Merchant B |
|---|---|---|
| Token Name | `Merchant A` | `Merchant B` |
| Grant Type | `Client Credentials` | `Client Credentials` |
| Access Token URL | Token endpoint from the address table | Same endpoint |
| Client ID | `merchant-a-client` | `merchant-b-client` |
| Client Secret | Your current A secret | Your current B secret |
| Scope | Leave empty | Leave empty |
| Client Authentication | `Send client credentials in body` | Same setting |

Select **Get New Access Token**, then **Use Token** (some versions also show
**Proceed**). The main request URL stays an API URL, for example
`http://localhost:8080/api/v1/payments`; the Access Token URL points to Keycloak.

Do not share/sync tokens or credential-containing exports. The **JWT Bearer**
option generates/signs a JWT in Postman; it is not needed to send a JWT that
Keycloak has already issued. Never copy Keycloak's signing key into Postman.

Tokens expire after five minutes. Obtain a fresh token and select it again when
needed. Do not assume automatic refresh: this Client Credentials configuration
does not issue a refresh token.

## Use OpenAPI, Swagger UI, and Postman

The executable HTTP contract is available while the API is running:

| Purpose | URL |
|---|---|
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |

OpenAPI is the source of truth for business paths, request and response schemas,
headers, validation constraints, status codes, examples, and required scopes.
Only `/api/v1/**` is included. Actuator remains a separate operational interface.

In Swagger UI, select **Authorize**, paste only a current access token in the
`bearerAuth` field, and close the dialog. Do not include the word `Bearer`.
Swagger UI keeps authorization only in the current page state and does not
persist it across reloads.

To create a Postman collection from the same contract:

1. Select **Import > Link**.
2. Enter `http://localhost:8080/v3/api-docs` and complete the import.
3. Open the generated collection's **Authorization** tab and select
   **Bearer Token**.
4. Paste a current Keycloak access token. Obtain or replace the token using the
   token request described above whenever it expires.

The generated collection provides the endpoint structure and schemas. The
scenario below supplies the sequence and expected behavior needed to validate
idempotency, authorization, ownership, and state transitions.

## Inspect health and protected metrics

Health probes are public so container orchestrators can call them without
managing an OAuth token. Details and component names remain hidden.

With **No Auth**, send:

```http
GET http://localhost:8080/actuator/health
GET http://localhost:8080/actuator/health/liveness
GET http://localhost:8080/actuator/health/readiness
```

With PostgreSQL available, each request must return `200 OK` and
`{"status":"UP"}`. The response must not contain `components`, database names,
hostnames, or credentials.

Still with **No Auth**, request `GET /actuator/metrics`: expect 401. Repeat with
a merchant A token: expect 403. Use the operations token for these requests:

```http
GET http://localhost:8080/actuator/metrics
GET http://localhost:8080/actuator/metrics/http.server.requests
GET http://localhost:8080/actuator/metrics/hikaricp.connections
GET http://localhost:8080/actuator/prometheus
```

Expect 200. The metrics endpoint lists diagnostic meter names; its selectors
show the measurements and low-cardinality tags recorded by Micrometer. The
Prometheus endpoint returns text intended for a monitoring scraper. Never place
tokens, merchant identifiers, payment IDs, idempotency keys, or payment method
tokens in metric names or tags.

With the same operations token, request `GET /api/v1/payments`: expect 403. This
proves the monitoring identity cannot read business data.

To verify the difference between liveness and readiness, stop only PostgreSQL
in **Dev Container Bash**:

```bash
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml stop postgres
```

`GET /actuator/health/liveness` must remain `200 UP`: the Java process is still
running. `GET /actuator/health/readiness` must become `503 DOWN` because the API
cannot serve database-backed traffic. The readiness request can take up to the
connection-pool timeout while the database is unavailable.

Restore PostgreSQL immediately after the check:

```bash
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml start postgres
```

Wait for PostgreSQL to become healthy. Readiness must return to `200 UP` without
restarting Spring Boot. Keycloak can also take a moment to recover its database
connection.

## Run the payment and security scenario

Use Swagger UI or the collection imported into Postman. The OpenAPI examples
provide request bodies and explain every field; this section defines only the
sequence and the behavior to prove.

Obtain fresh tokens for merchants A and B. Choose a new key prefix for each full
run, such as `local-check-001`, and save the created payment IDs as `ID_A` and
`ID_B`.

| Step | Identity and operation | Input variation | Expected evidence |
|---:|---|---|---|
| 1 | A creates a payment | Fresh key ending in `-create`; approved example body; reference `LOCAL-CHECK-A` | 201, APPROVED; save `ID_A` |
| 2 | A repeats step 1 | Same key and identical body | 200, same ID, `Idempotency-Replayed: true` |
| 3 | A repeats step 1 | Same key, change only amount | 409; original payment unchanged |
| 4 | B creates a payment | Reuse A's key; reference `LOCAL-CHECK-B` | 201 with a different ID; save `ID_B` |
| 5 | A and B read payments | Each reads its own ID, then the other merchant's ID | Own resource 200; foreign resource 404 |
| 6 | A and B list payments | First page, size 100 | Each list excludes the other merchant's IDs |
| 7 | B refunds `ID_B` | Fresh refund key and documented example body | 403 because B lacks `refunds:create` |
| 8 | A refunds `ID_B` | Fresh refund key | 404 because A does not own B's payment |
| 9 | A refunds `ID_A` | Fresh refund key | 201, COMPLETED; payment becomes REFUNDED |
| 10 | A repeats step 9 | Same key and identical body | 200, same refund ID, replay header true |
| 11 | A refunds `ID_A` again | Different key | 409; no second refund |
| 12 | A reads `ID_A` and its events | No body | Payment REFUNDED; creation, approval, and refund events |

Use the documented operations to check representative errors as well:

| Check | Expected |
|---|---|
| List without Authorization | 401 Problem Details |
| List with `token-invalid` | 401 Problem Details |
| Create with negative amount | 400 validation Problem Details with an `amount` error |
| Create without `Idempotency-Key` | 400 Problem Details |
| Create with currency USD | 400, only BRL is supported |
| List with page -1 and size 101 | 400 with `page` and `size` errors |
| Get `not-a-uuid` | 400 Problem Details |
| B attempts A's refund | 403 before ownership is evaluated |

Every error response must use `application/problem+json`; its HTTP status must
match the body status. Validation errors contain only `field` and `message`, and
rejected values or credentials must not appear. Every response includes
`X-Trace-Id`; Problem Details repeats the same value in `traceId`.

These checks create one payment and one refund in the persistent demo database.
They do not require deleting existing data. Use a new key prefix when repeating
the complete scenario.

## Trace correlation and safe logs

The API generates a new UUID trace ID for every HTTP request. Every response
contains it in the `X-Trace-Id` header. Problem Details responses also contain
the same value in the `traceId` field; successful response bodies remain
unchanged.

Each completed request produces one summary log containing only the HTTP method,
normalized route when available, status, and duration. A request rejected before
Spring MVC selects a route uses `route=unmapped`, so raw paths and query strings
are not copied into the log. Unexpected server failures add a controlled error
entry with the exception type and code origin, without the exception message.

The application does not log request or response bodies, Authorization headers,
JWTs, `Idempotency-Key`, payment method tokens, refund reasons, database
passwords, or rejected validation values. Application logs go to standard
output. When the packaged `api` service runs through Compose, Docker keeps at
most three 10 MB log files for that container.

Use this checkpoint after starting the API:

1. Send an authenticated `GET /api/v1/payments` and record its `X-Trace-Id`
   response header. The successful JSON body must not contain `traceId`.
2. Send the same request with **No Auth**. Expect 401 and verify that
   `X-Trace-Id` exactly matches the `traceId` property in the Problem Details
   body.
3. Find the 401 trace ID in the API console. If the packaged API is running via
   Compose, use `docker compose logs api` instead. Expect one request summary
   with `method=GET`, `route=unmapped`, `status=401`, and `durationMs`.
4. Send an authenticated invalid payment using the sandbox-only markers
   `phase11-private-key` as `Idempotency-Key` and
   `phase11-private-payment-token` as `paymentMethodToken`. Make another field
   invalid so the response is 400.
5. Search the API output for `phase11-private`. Neither marker may appear. The
   request summary must still be findable by its trace ID.

Trace IDs are operational data and are not stored in PostgreSQL. Removing the
container logs also removes old trace records; payment and event data remain in
the database according to their separate lifecycle.

## Inspect persistence with DBeaver

Create a **PostgreSQL** connection, or reuse your existing demo connection:

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `payments_demo` |
| Username | `payments_demo` |
| Password | Your `DEMO_DB_PASSWORD` value |

Select **Test Connection**, download the driver if prompted, then **Finish**.
The development Compose override publishes PostgreSQL only on localhost.
This connection uses database credentials, not a Keycloak token.

To browse rows, expand **Databases > payments_demo > Schemas > public > Tables**,
open a table and select **Data**. Use the data grid's **Refresh** after new
requests; an already open grid does not necessarily refresh itself.

For a precise check:

1. Right-click the demo connection and select **SQL Editor > New SQL Script**.
2. Confirm the editor's active connection is `payments_demo`.
3. Paste the query below and replace the two UUID placeholders, keeping quotes.
4. Select the **entire query**, from SELECT through the semicolon.
5. Press **Ctrl+Enter**.

```sql
SELECT
    p.id,
    p.merchant_id,
    p.amount_minor,
    p.status,
    (SELECT COUNT(*) FROM refunds r
     WHERE r.payment_id = p.id) AS refunds,
    (SELECT COUNT(*) FROM payment_events e
     WHERE e.payment_id = p.id) AS events,
    (SELECT COUNT(*) FROM idempotency_records i
     WHERE i.payment_id = p.id) AS idempotency_records
FROM payments p
WHERE p.id IN ('REPLACE_WITH_ID_A'::uuid, 'REPLACE_WITH_ID_B'::uuid)
ORDER BY p.merchant_id;
```

This query only reads data. Expected rows for the two new payments:

| merchant_id | amount_minor | status | refunds | events | idempotency_records |
|---|---:|---|---:|---:|---:|
| merchant-a-client | 10000 | REFUNDED | 1 | 3 | 2 |
| merchant-b-client | 10000 | APPROVED | 0 | 2 | 1 |

These are per-payment counts, not totals for the whole database. Previous
`merchant-postman` records belong to a different identity and are not migrated
to the Keycloak clients.

## Run automated tests

In **Dev Container Bash**, without enabling the demo profile:

```bash
./mvnw clean test
```

Tests use disposable PostgreSQL containers, not `payments_demo`. Check
`BUILD SUCCESS` and the counts of failures, errors, and skipped tests.
Reports are under `target/surefire-reports/`.

The last confirmed suite before the trace-correlation change had 274 test
executions. Signed-token integration tests use a temporary local signing
authority, not a live Keycloak server. They complement the manual real-Keycloak
workflow above. Use the current Maven summary as the source of truth after
running tests for this change.

## Troubleshooting

| Symptom | Check |
|---|---|
| API connection refused | Spring Boot startup completed; VS Code forwards port 8080; no competing API instance |
| Token endpoint connection refused | Docker/Keycloak are running; use host port 8180, not 8080 |
| Keycloak `invalid_client` | Matching client ID and current secret; no quotes/spaces; selected credential transport |
| Unexpected 401 from API | Renew and select the token; remove duplicate Authorization headers; verify issuer/audience and API logs |
| 403 refund with B | Expected: B has no `refunds:create`; changing a form field does not grant it |
| 404 for an old payment | Check payment ID and token identity; demo-era merchant IDs are not the new client IDs |
| 200 instead of first-time 201 | You reused an existing key/body; choose a fresh run prefix if starting a new run |
| DBeaver shows stale data | Refresh the Data grid; confirm `payments_demo` rather than `payments` |
| SQL error near `AS` | Execute the whole SELECT, not an isolated subquery |
| Build permissions or missing classes in `target` | Check the Dev Container user and concurrent IDE builds; do not reset database volumes |

Existing database passwords and Keycloak realms persist across restarts.
Changing `.env` alone does not change them. For an exposed client secret, rotate
the client credential in Keycloak and update matching local configuration;
do not publish the old or new value.

## Stop without deleting data

Stop Spring Boot with **Ctrl+C**. To stop the Compose environment, use a
**host terminal** in the project folder:

```powershell
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml stop
```

Stopping services preserves their database volume. Do not remove volumes as a
normal stop/restart step.

## Verification record

The author reported successful completion of the real-Keycloak/Postman/DBeaver
checkpoint on 2026-09-16 and separately confirmed Postman's OAuth 2.0
Client Credentials helper. Automated verification on 2026-09-15 passed 258 tests
with zero failures, errors, or skipped tests. The documented changed-payload
409 case is also covered by automated idempotency tests and earlier manual checks.

This is local development verification, not a production security audit.

## Further reference

- [Postman OAuth 2.0 helper](https://learning.postman.com/docs/use/send-requests/authorization/oauth-20)
- [Keycloak endpoints and flows](https://www.keycloak.org/securing-apps/oidc-layers)
- [DBeaver SQL execution](https://dbeaver.com/docs/dbeaver/SQL-Execution/)
