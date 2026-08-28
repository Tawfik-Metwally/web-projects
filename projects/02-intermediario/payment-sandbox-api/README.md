# Payment Sandbox API

A containerized REST API for simulating payment creation, queries, idempotency, and refunds. The project does not process real money or accept real card data.

## Status

The project is under active development. The Spring Boot scaffolding, PostgreSQL, Keycloak, Dev Container, and Testcontainers foundation are available; payment endpoints are not implemented yet.

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

Open the project in VS Code and run **Dev Containers: Reopen in Container**. Inside the Dev Container, verify the current foundation with:

```bash
./mvnw test
```

Local secrets, generated files, and private project notes are excluded from version control.

