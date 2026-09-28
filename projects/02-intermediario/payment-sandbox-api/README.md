# Payment Sandbox API

[![Payment Sandbox CI](https://github.com/Tawfik-Metwally/web-projects/actions/workflows/payment-sandbox-ci.yml/badge.svg)](https://github.com/Tawfik-Metwally/web-projects/actions/workflows/payment-sandbox-ci.yml)

A containerized REST API for simulating payment creation, queries, idempotency, and refunds. The project does not process real money or accept real card data.

## Status

The project is under active development. Payment creation, merchant-scoped queries, paginated listing, full refunds, and chronological event history are implemented. Creation and refund operations persist their state, events, and idempotency records within transactional boundaries.

Persistent idempotency is enforced per merchant, operation, and key. Identical retries return the existing resource, changed requests under the same key return HTTP 409, and concurrent creation or refund requests recover the database winner after the losing transaction rolls back. Keycloak has a versioned realm, confidential merchant clients, API audience, and business scopes. The API is an OAuth 2.0 Resource Server: Spring Security validates Bearer JWTs and maps the Keycloak authorized-party claim (`azp`) to the merchant principal. This is not a production-ready payment API: handled MVC errors and security rejections use Problem Details, requests have trace correlation, Actuator exposes controlled health and metrics, and GitHub Actions runs the automated verification. A dependency and image audit baseline is documented below; delivery hardening remains planned.

## Current verification

The latest verification was run by the author in the Dev Container on 2026-09-28 with `./mvnw clean verify`: 288 tests passed with no failures, errors, or skipped tests. Spotless confirmed all 96 Java files are formatted, PMD 7.28.0 reported no violations from the focused ruleset, and JaCoCo generated coverage data for 56 classes. The same checkpoint confirmed Tomcat 11.0.26 in the dependency tree and repeated the container image audit after rebuilding the application image.

- domain, simulator, request-validation, mapping, hashing, service, and transaction tests;
- Spring MVC controller tests with mocked service dependencies;
- Resource Server security tests for missing, invalid, and valid Bearer tokens;
- PostgreSQL persistence tests with Testcontainers;
- full application integration tests for payment creation, queries, refunds, replay, conflicts, history, merchant isolation, and concurrent idempotency.

Business-flow integration tests use prepared JWT authentication to exercise controllers, services, domain, repositories, Hibernate, and temporary PostgreSQL. Concurrent tests force two transactions to compete for real unique constraints and verify recovery without partial or duplicate data. Focused Resource Server tests exercise the real security chain with a mocked decoder.

`JwtMerchantIsolationIntegrationTests` additionally sends genuinely signed Bearer tokens through the real decoder, claim converter, HTTP layer, and PostgreSQL. It verifies ownership in both directions, merchant-scoped pagination, payment and refund idempotency, ignored spoofed merchant headers/query parameters, and rejection without persistence. Its temporary signing authority is not the running Keycloak instance. Both test merchants deliberately share a subject and have all business scopes so these tests isolate ownership rather than scope authorization.

`JwtScopeAuthorizationIntegrationTests` adds 49 signed-token/PostgreSQL cases: all five endpoints with exact, missing, empty, unrelated, lookalike, or combined scopes; absent and invalid tokens; unconfigured routes; authorization before body parsing and ownership checks; and unchanged database state after rejection. These tokens use a temporary test issuer, not live Keycloak.

`JwtAuthenticationIntegrationTests` adds 18 cases: expired tokens, incorrect issuers and incorrect audiences through all five HTTP endpoints; identity and scope tampering after signing; and authentication isolation across requests sharing a simulated session. Rejections preserve the existing payment and related row counts. The author also confirmed the live Keycloak/Postman/DBeaver checkpoint on 2026-09-16.

The current identity model is one Keycloak client per merchant: validated `azp` becomes the merchant ID, not `sub`. Renaming a client changes that identity; supporting several clients for one merchant would require a separate mapping design. Header and query values cannot override it. Endpoint scope enforcement is implemented for all five business routes.

For a new payment, the controller returns `201 Created` and a `Location` header, including when the financial result is `DECLINED`. An identical retry returns `200 OK` with `Idempotency-Replayed: true`; changed content under the same key returns `409 Conflict`. Query, list, refund, and event-history routes preserve merchant isolation.

The author reported successful manual verification with real Keycloak tokens in Postman and persistent `payments_demo` data in DBeaver: authentication, endpoint scopes, cross-merchant isolation, payment/refund replay, and expected final row counts. Both manual Bearer token entry and Postman's OAuth 2.0 Client Credentials helper were confirmed. See the [reproducible walkthrough](docs/local-testing.md).

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
|-- domain           Payment and Money business rules
`-- simulator        deterministic provider simulation
```

`Payment` remains separate from `PaymentEntity`; this package arrangement does not merge business rules with persistence mappings. `SecurityConfiguration` defines the stateless Resource Server boundary and maps Keycloak's `azp` claim to `Principal.getName()`.

Tests live in `src/test/java` and mirror the package of the component they test. Application tests, cross-repository persistence integration tests, and shared Testcontainers support remain in the base package. JUnit runs the tests, Mockito replaces selected dependencies, and AssertJ checks results.

## Stack

- Java 25 and Spring Boot 4.1.1
- Spring Web MVC, Spring Security, and Spring Data JPA
- PostgreSQL 17.11 and Flyway
- Keycloak 26.7.4 with OAuth 2.0 and JWT
- Maven, JUnit, Mockito, and Testcontainers
- Docker Compose and VS Code Dev Containers

## Local development

Start with the [complete local testing guide](docs/local-testing.md). It contains:

- environment preparation and demo database setup;
- every Postman field for manual Bearer tokens and OAuth 2.0 Client Credentials;
- payment, refund, history, pagination, and security checks with expected responses;
- DBeaver connection fields and a read-only verification query;
- token-expiry, connection, and persistence troubleshooting.

### Quick start for an already configured environment

Requirements: Docker Desktop with Linux containers and VS Code Dev Containers.
For first-time setup, follow the guide before running these commands.

Open this project folder in VS Code and select **Dev Containers: Reopen in Container**.
In the Dev Container's Bash terminal at `/workspace`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

This uses `payments_demo` with JWT validation enabled. Keep the terminal open;
stop the application with **Ctrl+C**. The removed `demo-no-auth` profile must not
be used. Without the `demo` profile, the application uses the development database.

- API: [localhost:8080](http://localhost:8080)
- Public health check: [localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- Public probes: `/actuator/health/liveness` and `/actuator/health/readiness`
- Protected diagnostics: `/actuator/metrics` and `/actuator/prometheus`
- OpenAPI contract: [localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Swagger UI: [localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- Keycloak: [localhost:8180](http://localhost:8180)

Request a token from Keycloak as described in the guide, then send it to the API.
The OpenAPI URL can also be imported directly into Postman to create a collection.
A first payment request with a fresh idempotency key is:

```http
POST http://localhost:8080/api/v1/payments
Authorization: Bearer <access_token>
Idempotency-Key: quickstart-001
Content-Type: application/json
```

```json
{
  "amount": 10000,
  "currency": "BRL",
  "merchantReference": "ORDER-QUICKSTART",
  "paymentMethodToken": "tok_approved"
}
```

Expected: 201 and an approved payment. An unchanged retry returns 200 without a
second payment. Never put real tokens or credentials in committed files.

### Run tests

In the Dev Container, without the demo profile:

```bash
./mvnw clean test
```

The suite uses disposable PostgreSQL containers. Spring Boot does not need to be
running. Check the exit status, test totals, and reports in `target/surefire-reports/`.
The last verified suite has 288 executions, all passing.

### Check formatting and static analysis

Spotless keeps Java formatting deterministic. Apply the configured format after
editing Java code, then check that no formatting changes remain:

```bash
./mvnw spotless:apply
./mvnw spotless:check
```

PMD inspects the source for the focused defect and maintainability rules in
`config/pmd/ruleset.xml`:

```bash
./mvnw pmd:check
```

To run the tests, both quality checks, and generate a JaCoCo coverage report, use:

```bash
./mvnw clean verify
```

Open `target/site/jacoco/index.html` after the build. The report highlights executed
lines and branches as a diagnostic aid; no percentage threshold fails the build.
The Maven `verify` phase fails when Spotless or PMD reports a violation.

### Continuous integration

The path-filtered `Payment Sandbox CI` workflow runs `clean verify` with Temurin
Java 25 for relevant pushes and pull requests, and it can also be started
manually. Tests use disposable PostgreSQL containers through Testcontainers, so
the workflow does not require a shared database or application secrets.

Surefire, JaCoCo, and PMD reports produced under the ignored `target/` directory
are uploaded as a workflow artifact for seven days. Artifacts belong to a
specific workflow run and are not committed to the repository.

### Audit dependencies and container images

The Spring Boot parent manages application dependency versions. When investigating
a transitive dependency, display its origin instead of adding it directly:

```bash
./mvnw dependency:tree -Dincludes=org.apache.tomcat.embed:tomcat-embed-core
```

The expected Tomcat version is `11.0.26`. It is temporarily overridden because
Spring Boot 4.1.1 manages an older release. Remove the override when a tested
Spring Boot update manages the same or a newer secure version.

Docker image references keep a readable tag and an immutable multi-platform
digest. To review the digest currently published for a tag without pulling or
starting the image, run:

```bash
docker buildx imagetools inspect postgres:17.11-alpine3.24
docker buildx imagetools inspect quay.io/keycloak/keycloak:26.7.4
docker buildx imagetools inspect eclipse-temurin:25.0.4_7-jdk-noble
docker buildx imagetools inspect eclipse-temurin:25.0.4_7-jre-noble
```

Build the application image before auditing it. The Dockerfile packaging step
skips tests, so run `./mvnw clean verify` separately:

```bash
docker build --pull -t payment-sandbox-api:audit .
docker scout cves --only-severity critical,high --only-fixed local://payment-sandbox-api:audit
```

Inspect the current registry versions of the infrastructure images separately:

```bash
docker scout cves --only-severity critical,high registry://postgres:17.11-alpine3.24
docker scout cves --platform linux/amd64 --only-severity critical,high registry://quay.io/keycloak/keycloak:26.7.4
```

`--only-severity` filters the displayed severities; it does not prove that lower
severity findings are absent. `--only-fixed` shows findings for which the scanner
knows a remediation. A successful command means that the scan completed, not
that the image has zero vulnerabilities. Evaluate each result against the
maintainer's advisory and the component's actual use. Docker Scout sends package
identifiers and image-layer metadata to Docker's service for analysis; it does
not upload the complete image.

To stop the environment without deleting database data, run this in a **host
terminal** in the project folder after stopping Spring Boot:

```powershell
docker compose --env-file .env -f compose.yaml -f .devcontainer/compose.extend.yaml stop
```

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

The packaged API uses the same localhost addresses and has the same unfinished features described above. The latest verification covers compilation, the Maven test suite, Spotless, PMD, and JaCoCo; a full smoke test of the packaged runtime remains planned. This Compose configuration is for local use, not production deployment.

## Infrastructure and local files

- `.devcontainer/`: development container configuration and its Dockerfile;
- `docker/keycloak/`: versioned local realm, clients, scopes, and audience;
- `docker/postgres/`: initialization of database users and databases;
- `src/main/resources/db/migration/`: Flyway migrations for application tables;
- `.mvn/`, `mvnw`, and `mvnw.cmd`: Maven Wrapper;
- `.env.example`: versioned template without real credentials;
- `.env` and `.vscode/`: local configuration, ignored by Git;
- `target/`: generated classes, artifacts, and test reports, ignored by Git.

Local secrets, generated files, and private project notes are excluded from version control.
