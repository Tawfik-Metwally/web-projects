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
- [Run the payment and security checks](#run-the-payment-and-security-checks)
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
`MERCHANT_A_CLIENT_SECRET` and `MERCHANT_B_CLIENT_SECRET`. Keep database names
consistent with the template unless you intentionally redesign the setup.

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

| Merchant | Client ID | Secret source in local `.env` | Default business scopes |
|---|---|---|---|
| A | `merchant-a-client` | `MERCHANT_A_CLIENT_SECRET` | `payments:create payments:read refunds:create` |
| B | `merchant-b-client` | `MERCHANT_B_CLIENT_SECRET` | `payments:create payments:read` |

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

## Run the payment and security checks

Use a fresh key prefix for each complete run, such as `local-check-001`.
If rerunning the whole guide, change it everywhere to `local-check-002`.
For replay checks within one run, keep the original key and body unchanged.

`ID_A` and `ID_B` below are placeholders, not Postman variables. Replace them
with actual UUIDs from responses. GET requests have **Body > none**. POST API
requests use **Body > raw > JSON**, not the form body used by the token endpoint.

### 1. Create a payment for A and verify idempotency

Send with A's token:

```http
POST http://localhost:8080/api/v1/payments
Idempotency-Key: local-check-001-create
Content-Type: application/json
```

```json
{
  "amount": 10000,
  "currency": "BRL",
  "merchantReference": "LOCAL-CHECK-A",
  "paymentMethodToken": "tok_approved"
}
```

Expected: `201 Created`, `status: APPROVED`, and `amount: 10000` (BRL 100.00).
Save the response `id` as ID_A.

Send again unchanged: expect `200 OK`, the same ID and response header
`Idempotency-Replayed: true`.

Change only `amount` to `11000`, keeping the same key: expect `409 Conflict`.
Restore the body to `10000`. The conflict must not change the existing payment.

### 2. Create a payment for B with the same key

Send the same POST with B's token and key `local-check-001-create`, changing
only `merchantReference` to `LOCAL-CHECK-B`.

Expected: `201`, `APPROVED`, a different ID. Save it as ID_B. The same key can
be used independently by different merchants.

### 3. Read payments, list them, and inspect history

All paths below use base URL `http://localhost:8080`.

| Token | Method and path | Expected |
|---|---|---|
| A | `GET /api/v1/payments/ID_A` | 200, payment A |
| B | `GET /api/v1/payments/ID_B` | 200, payment B |
| B | `GET /api/v1/payments/ID_A` | 404 |
| A | `GET /api/v1/payments/ID_B` | 404 |
| A | `GET /api/v1/payments/ID_A/events` | 200, creation and approval events |
| B | `GET /api/v1/payments/ID_A/events` | 404 |

List using A, then B:

```http
GET http://localhost:8080/api/v1/payments?page=0&size=100
```

A's list must not include ID_B, and B's list must not include ID_A.
Old records belonging to the same merchant may also appear. `page=0` selects
the first page; `size` is the maximum number of items on that page (1 to 100).
Results are ordered by newest creation time, then ID, descending.

### 4. Check authentication

Use `GET /api/v1/payments/ID_A`:

1. With a fresh A token: 200.
2. Set Authorization to **No Auth**: 401.
3. Set Bearer Token to `token-invalid`: 401.
4. Restore a fresh A token: 200.

Ensure no manual Authorization header remains in the No Auth step.
A successful request does not authenticate later requests automatically.

### 5. Check scope and ownership before refunding

For all refund requests, use this JSON body:

```json
{
  "reason": "CUSTOMER_REQUEST"
}
```

Do not send an amount; the API derives the full amount from the payment.

| Token | Method and path | Idempotency-Key | Expected |
|---|---|---|---|
| B | `POST /api/v1/payments/ID_B/refunds` | `local-check-001-refund-b` | 403: B lacks the refund scope |
| A | `POST /api/v1/payments/ID_B/refunds` | `local-check-001-refund-cross` | 404: A has the scope but does not own B's payment |
| A | `POST /api/v1/payments/ID_A/refunds` | `local-check-001-refund-a` | 201: refund created |

The successful refund response has `paymentId: ID_A`, `amount: 10000`, and
`status: COMPLETED`. This is the **refund's** status; the payment becomes
`REFUNDED`.

Repeat A's successful refund with the same key and body: expect 200, the same
refund ID, and `Idempotency-Replayed: true`.

Change only its key to `local-check-001-refund-a-second`: expect 409 because
the payment has already been refunded. Neither attempt creates another refund.

### 6. Verify the final API state

With A's token:

- `GET /api/v1/payments/ID_A`: 200, `REFUNDED`.
- `GET /api/v1/payments/ID_A/events`: 200, exactly three events:
  `PAYMENT_CREATED`, `PAYMENT_APPROVED`, `PAYMENT_REFUNDED`.
- `GET /api/v1/payments?status=REFUNDED&page=0&size=100`: includes A's newly
  refunded payment in this small local scenario.

With B's token, that filtered list must not include ID_A. Reading ID_B still
returns `APPROVED`.

### Status reference

| Status | Meaning in this guide |
|---|---|
| 200 | Successful read or idempotent replay |
| 201 | New payment or refund created |
| 401 | Missing, invalid, or expired token |
| 403 | Valid identity without the required scope |
| 404 | Payment absent or owned by another merchant, after scope checks |
| 409 | Idempotency conflict or payment no longer refundable |

A declined payment can still return 201: HTTP creation success is different from
financial approval. Unconfigured API method/path combinations are denied.
Some current error responses have empty bodies; standardized error bodies are
not implemented yet.

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

The verified suite has 258 test executions. Signed-token integration tests use
a temporary local signing authority, not a live Keycloak server. They complement
the manual real-Keycloak workflow above.

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
