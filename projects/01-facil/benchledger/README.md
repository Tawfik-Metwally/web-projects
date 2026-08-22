# BenchLedger

[![BenchLedger CI](https://github.com/Tawfik-Metwally/web-projects/actions/workflows/benchledger-ci.yml/badge.svg)](https://github.com/Tawfik-Metwally/web-projects/actions/workflows/benchledger-ci.yml)

BenchLedger is a containerized web application for small laboratories and makerspaces that need a clear equipment inventory, calibration deadlines, and an auditable technical history.

## What it does

- Registers and updates laboratory equipment without changing its asset tag.
- Removes any equipment from the operational inventory while preserving its full history in deleted records, with restoration support.
- Records successful or failed calibrations and derives the next due date.
- Records preventive or corrective maintenance.
- Removes equipment from service and returns it without deleting its history.
- Corrects mistaken calibration or maintenance entries while preserving the original record.
- Filters the inventory by operational status and free-text search.
- Explains why each item is active, due soon, overdue, calibration failed, or out of service.

## Requirements

- Docker Desktop with Docker Compose.
- Git only if you want to clone the repository.

Node.js, pnpm, PostgreSQL, NestJS, and Vite do not need to be installed on the host.

## Run locally

From this directory:

```bash
docker compose up -d --wait
```

Open:

- Web interface: <http://localhost:5173>
- API health check: <http://localhost:3000/api/health>

The default local credentials are development-only values declared in `compose.yaml`. To customize ports or credentials, copy `.env.example` to `.env` and edit the local file. `.env` is ignored by Git; `.env.example` is intentionally public because it contains only safe example values.

## Stop and resume

Stop containers while preserving the database:

```bash
docker compose stop
```

Resume them later:

```bash
docker compose start
```

Remove containers while preserving the named database volume:

```bash
docker compose down
```

To permanently erase the local database, run `docker compose down --volumes`. This is destructive and cannot be undone unless you have a backup.

## Verify the project

Run lint rules, TypeScript checks, automated tests, and production builds inside the tools container:

```bash
docker compose build app
docker compose run --rm --no-deps app pnpm verify
```

The GitHub Actions workflow also starts a clean application and exercises the critical create, edit, calibrate, correct, remove, restore, and list flow against PostgreSQL.

## Status rules

| Status | Meaning |
| --- | --- |
| Active | Available for use and outside the calibration warning window. |
| Due soon | Calibration is due within 30 days. |
| Overdue | Calibration is required but expired or has never passed. |
| Calibration failed | The latest effective calibration failed; use should be restricted. |
| Out of service | The equipment was explicitly removed from operational use. |

An equipment correction never silently rewrites history. The original event remains visible, the reason is attached to it, and the corrected event stops affecting the derived status.

## Technology

TypeScript, React, Material UI, Vite, NestJS, Prisma, PostgreSQL, pnpm, Docker Compose, and GitHub Actions.

## Current limitations

- This version has no authentication or user roles and is intended for local demonstration, not exposure to the public internet.
- It does not store calibration certificates or send deadline notifications.
- It is not a regulatory compliance system.
- Backup automation and production deployment are outside this first release.
