#!/bin/sh
# Runs once, on the first start of an empty postgres_data volume: gives Keycloak its
# own database and login, separate from the app's, on the same server.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v kc_db="$KEYCLOAK_DB" -v kc_user="$KEYCLOAK_DB_USER" -v kc_pass="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE :"kc_user" LOGIN PASSWORD :'kc_pass';
CREATE DATABASE :"kc_db" OWNER :"kc_user";
REVOKE ALL ON DATABASE :"kc_db" FROM PUBLIC;
SQL
