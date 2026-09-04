# Payment Sandbox API

A containerized REST API for simulating payment creation, queries, idempotency, and refunds. The project does not process real money or accept real card data.

## Status

The project is under active development. The persistence model, payment domain, and initial `POST /api/v1/payments` controller and service are implemented. The service coordinates deterministic approval or decline and saves the payment with its creation and decision events within a Spring transaction.

This is not a production-ready payment API. Persistent idempotency and concurrency handling, JWT validation and merchant identity mapping, query and refund endpoints, and standardized error responses are still pending. `Idempotency-Key` is currently validated and transported, but does not prevent duplicate payments yet. Existing Spring Security defaults remain in place; a real Bearer-token flow is not configured yet.

## Current verification

The latest full test run, after the technical-layer package reorganization, passed 59 tests:

- 23 domain tests and 12 request-validation tests;
- 2 domain/persistence mapping tests and 2 service tests with mocked repositories;
- 14 Spring MVC controller tests with a mocked service and test authentication;
- 4 PostgreSQL persistence integration tests and 2 application context/health tests.

Controller tests verify request mapping, exact service arguments, validation, and HTTP responses. They do not establish end-to-end HTTP-to-database behavior, real JWT validation, or transactional rollback against PostgreSQL. These checks remain planned.

For a new payment, the controller returns `201 Created` and a `Location` header, including when the financial result is `DECLINED`. It can also translate a service-reported replay into `200 OK` with `Idempotency-Replayed: true`; that branch is currently exercised with a mock, not real idempotency. The query route advertised by `Location` is not implemented yet.

The reorganized packages were validated with `./mvnw clean test`: 59 tests, no failures, errors, or skipped tests. The clean build removes compiled classes from the old package locations.

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

`Payment` remains separate from `PaymentEntity`; this package arrangement does not merge business rules with persistence mappings. Security-specific packages will be added when the JWT integration is implemented.

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

- Docker Desktop
- Visual Studio Code with the Dev Containers extension

Prepare the local environment file and replace every placeholder value:

```bash
cp .env.example .env
```

Open the project in VS Code and run **Dev Containers: Reopen in Container**. Inside the Dev Container, run the test suite with:

```bash
./mvnw test
```

Local secrets, generated files, and private project notes are excluded from version control.
