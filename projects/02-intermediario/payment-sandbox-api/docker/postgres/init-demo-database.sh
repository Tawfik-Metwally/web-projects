#!/bin/sh

set -eu

: "${POSTGRES_USER:?POSTGRES_USER must identify the local PostgreSQL administrator}"
: "${DEMO_DB_PASSWORD:?Set DEMO_DB_PASSWORD in .env and recreate the containers first}"

# Fixed targets: never reuse a configured development/Keycloak/admin name.
for existing_name in "$POSTGRES_USER" "${POSTGRES_DB:-postgres}" \
  "${APP_DB_NAME:-payments}" "${APP_DB_USER:-payments}" \
  "${KEYCLOAK_DB_NAME:-keycloak}" "${KEYCLOAK_DB_USER:-keycloak}"; do
  if [ "$existing_name" = payments_demo ]; then
    printf '%s\n' 'Refusing demo setup: payments_demo is already configured for another purpose.' >&2
    exit 1
  fi
done

psql -X --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname postgres \
  --set=demo_password="$DEMO_DB_PASSWORD" <<-'SQL'
SELECT format('CREATE ROLE payments_demo LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'demo_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'payments_demo') \gexec

-- Refuse to reuse an existing privileged role. Do not modify its privileges.
DO $guard$
BEGIN
  IF EXISTS (
    SELECT FROM pg_roles
    WHERE rolname = 'payments_demo'
      AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)
  ) OR EXISTS (
    SELECT FROM pg_auth_members WHERE member = 'payments_demo'::regrole
  ) THEN
    RAISE EXCEPTION 'Refusing demo setup: the existing payments_demo role has unexpected privileges.';
  END IF;
END;
$guard$;

SELECT 'CREATE DATABASE payments_demo OWNER payments_demo'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payments_demo') \gexec

DO $guard$
BEGIN
  IF EXISTS (
    SELECT FROM pg_database
    WHERE datname = 'payments_demo' AND pg_get_userbyid(datdba) <> 'payments_demo'
  ) THEN
    RAISE EXCEPTION 'Refusing demo setup: payments_demo belongs to another role.';
  END IF;
END;
$guard$;

REVOKE ALL ON DATABASE payments_demo FROM PUBLIC;
\echo 'payments_demo is prepared. Existing data and passwords were not changed.'
SQL
