# Payment Sandbox API

A containerized REST API for simulating payment creation, queries, idempotency, and refunds. The project does not process real money or accept real card data.

## Status

The project is under active development. The persistence model, payment domain, and `POST /api/v1/payments` flow are implemented. The service coordinates deterministic approval or decline and saves the payment, its creation and decision events, and the idempotency record within transactional boundaries.

Persistent idempotency is enforced per merchant, operation, and key. An identical retry returns the existing payment, a changed request with the same key returns HTTP 409, and concurrent requests recover the winning transaction after the losing transaction rolls back. This is not a production-ready payment API: JWT validation and real merchant identity mapping, query and refund endpoints, and standardized error responses are still pending.

## Current verification

The latest clean build passed 83 tests with no failures, errors, or skipped tests:

- domain, simulator, request-validation, mapping, hashing, service, and transaction tests;
- Spring MVC controller tests with mocked service dependencies;
- demo-profile security tests for missing, invalid, and valid merchant headers;
- PostgreSQL persistence tests with Testcontainers;
- full application integration tests for creation, replay, conflict, and concurrent idempotency.

The integration suite connects the HTTP layer, demo identity filter, controller, service, domain, repositories, Hibernate, and temporary PostgreSQL. The concurrent test forces two transactions to compete for the real unique constraint and verifies one HTTP 201 response, one replayed HTTP 200 response, and final persisted counts of one payment, two events, and one idempotency record. These tests do not establish real JWT validation.

For a new payment, the controller returns `201 Created` and a `Location` header, including when the financial result is `DECLINED`. An identical retry returns `200 OK` with `Idempotency-Replayed: true`; changed content under the same key returns `409 Conflict`. The query route advertised by `Location` is not implemented yet.

The same create, replay, and conflict flow was also verified manually with Postman against the persistent `payments_demo` database. DBeaver confirmed that only HTTP 201 requests created rows and that each created payment has exactly two events and one idempotency record.

## Code organization

The application uses technical-layer packages under `io.github.tawfikmetwally.payments`:

```text
payments
|-- PaymentSandboxApiApplication.java
|-- config
|-- controller
|-- service          services and their Command/Result contracts
|-- dto
|   |-- request      HTTP request bodies
|   `-- response     HTTP response bodies
|-- entity           JPA persistence mappings
|-- repository       Spring Data repositories
|-- enums
|-- exception
|-- security         explicit local demo identity filter
|-- domain           Payment and Money business rules
`-- simulator        deterministic provider simulation
```

`Payment` remains separate from `PaymentEntity`; this package arrangement does not merge business rules with persistence mappings. The current `security` package contains only the explicit local demonstration filter. Real JWT resource-server configuration remains pending.

Tests live in `src/test/java` and mirror the package of the component they test. Application tests, cross-repository persistence integration tests, and shared Testcontainers support remain in the base package. JUnit runs the tests, Mockito replaces selected dependencies, and AssertJ checks results.

## Stack

- Java 25 and Spring Boot 4.1.1
- Spring Web MVC, Spring Security, and Spring Data JPA
- PostgreSQL 17.11 and Flyway
- Keycloak 26.7.2 with OAuth 2.0 and JWT
- Maven, JUnit, Mockito, and Testcontainers
- Docker Compose and VS Code Dev Containers

## Local development

Requirements:

- Docker Desktop running with Linux containers;
- Visual Studio Code with the Dev Containers extension for the development workflow.

### Prepare the environment

Open a local terminal in `web/projects/02-intermediario/payment-sandbox-api`, not the parent repository.

If `.env` does not exist yet, create it from `.env.example`. In Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Or in Bash:

```bash
cp .env.example .env
```

Replace every placeholder before starting the containers. Do not overwrite an existing `.env` or commit it. Database initialization uses these values when the PostgreSQL data volume is first created; editing `.env` later does not automatically update existing database users or passwords.

### Start the development environment

Open this project directory in VS Code and run **Dev Containers: Reopen in Container**. VS Code combines `compose.yaml` and `.devcontainer/compose.extend.yaml` to start `dev`, `postgres`, and `keycloak`. The development override excludes the packaged `api` service with the `packaged-api` profile.

In the Dev Container's Bash terminal, the project is mounted at `/workspace`. Start Spring Boot:

```bash
./mvnw spring-boot:run
```

Keep that terminal open while using the API. Stop Spring Boot with **Ctrl+C** in the same terminal; this does not stop PostgreSQL or Keycloak.

Source edits do not require rebuilding the Dev Container. Stop and restart the application to load changes. Use **Dev Containers: Rebuild Container** when changing the development image or its features.

### Optional demonstration database (Dev Container)

The same PostgreSQL container can host `payments_demo`, owned by the dedicated
`payments_demo` role, alongside the development and Keycloak databases. This is
a separate logical database, not another container. Setup scripts use fixed demo
names and refuse collisions with configured development/Keycloak/admin names.

1. Add `DEMO_DB_PASSWORD` to your existing local `.env`, using your own password.
   Do not replace the file or change the existing database credentials.
2. Stop Spring Boot. From a **local host terminal** in the project directory,
   update the PostgreSQL container so it receives the new variable and script mount:

   ```bash
   docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml up -d postgres
   ```

   This can recreate the PostgreSQL container, briefly interrupting connections,
   but preserves its existing data volume. Never delete the shared volume to
   prepare or reset the demo database.
3. Run **Dev Containers: Rebuild Container** in VS Code once so `dev` receives the
   new environment variables and no longer inherits `SPRING_DATASOURCE_*`.
4. For an existing PostgreSQL volume, run this once in the **Dev Container's Bash
   terminal** at `/workspace`:

   ```bash
   docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml exec postgres sh /opt/payment-sandbox/init-demo-database.sh
   ```

   The script creates the role and database if absent. It does not delete records,
   change an existing password, or recreate an existing database. For a fresh
   volume, the initial setup invokes it automatically when `DEMO_DB_PASSWORD` is set.
   Flyway creates the application tables when the application connects; no separate
   demo migration is needed.

Choose the database when starting Spring Boot inside the **Dev Container**:

```bash
# Development database
./mvnw spring-boot:run
```

```bash
# Demonstration database
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

For local Postman verification before the real JWT phase, activate the separate,
explicit `demo-no-auth` profile together with `demo`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo,demo-no-auth
```

Stop the running application with **Ctrl+C** before switching. Subsequent switches
do not require container rebuilds. The demo profile changes the datasource only:
it does not disable security, enable JWT, or clear any data. The separate
`demo-no-auth` profile installs a stateless filter that accepts
`X-Demo-Merchant-Id` and exposes it as the `Principal` expected by the controller.
This header is untrusted demonstration input, not authentication, and must never
be enabled as a production security mechanism.

Example local creation request:

```http
POST http://localhost:8080/api/v1/payments
X-Demo-Merchant-Id: merchant-demo
Idempotency-Key: payment-demo-001
Accept: application/json
Content-Type: application/json
```

```json
{
  "amount": 10000,
  "currency": "BRL",
  "merchantReference": "ORDER-DEMO-001",
  "paymentMethodToken": "tok_approved"
}
```

The first request returns HTTP 201. Repeating the same request returns HTTP 200
with `Idempotency-Replayed: true`; changing the content while keeping the same key
returns HTTP 409.

This profile workflow targets `dev`, not the packaged `api` service, which still
uses explicit `SPRING_DATASOURCE_*` variables. Do not set those variables in the
Dev Container: they take precedence over profile files. Tests continue to use
Testcontainers connection details; do not activate `demo` for the test suite.

The demo profiles were validated manually against `payments_demo`: Flyway applied
V1, Hibernate accepted the schema, and Postman verified HTTP 201 creation, HTTP 200
replay, and HTTP 409 conflict. PostgreSQL and DBeaver confirmed that replay and
conflict do not duplicate persisted data. The clean test suite then passed all 83
tests. Real authenticated HTTP verification remains deferred until JWT integration.

### Addresses and current limitations

With Spring Boot running and port 8080 forwarded by VS Code:

- API base address: [localhost:8080](http://localhost:8080);
- health endpoint: [localhost:8080/actuator/health](http://localhost:8080/actuator/health);
- Keycloak administration: [localhost:8180](http://localhost:8180), using the local administrator credentials configured in `.env`.

PostgreSQL is reachable inside the Compose network at `postgres:5432`. When the
development override is active, it is also bound only to `127.0.0.1:5432` on the
host for local tools such as DBeaver. The base Compose file does not publish the
database port. Database names and credentials come from `.env`; use the dedicated
`payments_demo` role and `DEMO_DB_PASSWORD` when connecting DBeaver to the
`payments_demo` database.

Health reports application health, not completion of all payment features. `POST /api/v1/payments` is not a browser GET page. Outside the explicit `demo-no-auth` profile, Spring Security defaults remain active, and the Keycloak login is separate from API authentication. Obtaining a Keycloak token does not yet enable a working JWT payment flow. Query endpoints, refunds, and real JWT configuration remain pending.

### Run tests

Run these commands in the Dev Container's Bash terminal at `/workspace`. Spring Boot does not need to be running. Integration tests use disposable PostgreSQL containers through Testcontainers, not the persistent development database.

Complete suite:

```bash
./mvnw test
```

Clean rebuild, for example after moving or deleting Java classes:

```bash
./mvnw clean test
```

Select one test class; each line below is an independent command:

```bash
./mvnw '-Dtest=PaymentTests' test
./mvnw '-Dtest=CreatePaymentServiceTests' test
./mvnw '-Dtest=PaymentControllerTests' test
./mvnw '-Dtest=PersistenceIntegrationTests' test
./mvnw '-Dtest=DemoSecurityConfigurationTests' test
./mvnw '-Dtest=PaymentCreationIntegrationTests' test
```

Select only the merchant-isolation test:

```bash
./mvnw '-Dtest=PersistenceIntegrationTests#findsPaymentOnlyForItsMerchant' test
```

Domain tests check rules without mocks. Service tests mock repositories, controller tests mock the service, and persistence integration tests use a real temporary database. Selecting tests limits execution; Maven may still compile other sources.

Check both `BUILD SUCCESS` and `Tests run / Failures / Errors / Skipped`. Per-class summaries appear in the terminal; detailed reports are generated in `target/surefire-reports/`. The latest validated full suite contains 83 test executions.

### Stop the development environment

After stopping Spring Boot, close the Dev Container window; `shutdownAction: stopCompose` requests that VS Code stop its Compose environment. To stop it manually, use a **local host terminal** in the project directory:

```bash
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml stop
```

This stops containers without deleting the database volume.

## Packaged application workflow

This is an alternative to the Dev Container workflow. Stop the development environment first; do not run both application modes at once, because they share infrastructure and port 8080.

From a **local host terminal** in the project directory, with `.env` configured:

```bash
docker compose --env-file .env -f compose.yaml up -d --build
```

Using only the base Compose file starts the packaged `api`, `postgres`, and `keycloak` services. The root `Dockerfile` builds the JAR with a JDK and runs it in a separate JRE image as a non-root user. Packaging skips test execution, so building the image does not replace running the test suite.

Inspect service status and follow application logs:

```bash
docker compose --env-file .env -f compose.yaml ps
docker compose --env-file .env -f compose.yaml logs --follow api
```

**Ctrl+C** stops following logs, not the containers. To stop them while preserving database data:

```bash
docker compose --env-file .env -f compose.yaml stop
```

The packaged API uses the same localhost addresses and has the same unfinished features described above. The latest verification covers the Maven test suite; a full smoke test of the packaged runtime remains planned. This Compose configuration is for local use, not production deployment.

## Infrastructure and local files

- `.devcontainer/`: development container configuration and its Dockerfile;
- `docker/postgres/`: initialization of database users and databases;
- `src/main/resources/db/migration/`: Flyway migrations for application tables;
- `.mvn/`, `mvnw`, and `mvnw.cmd`: Maven Wrapper;
- `.env.example`: versioned template without real credentials;
- `.env` and `.vscode/`: local configuration, ignored by Git;
- `target/`: generated classes, artifacts, and test reports, ignored by Git.

Local secrets, generated files, and private project notes are excluded from version control.
