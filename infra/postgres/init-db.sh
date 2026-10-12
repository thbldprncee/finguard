#!/bin/bash
# Creates one database and one login role per service.
# Each service can only connect to its own database.
set -euo pipefail

create_service_db() {
  local db="$1" role="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v db="$db" -v role="$role" -v password="$password" <<'EOSQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"role";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
}

create_service_db keycloak_db     keycloak_svc     "$KEYCLOAK_DB_PASSWORD"
create_service_db transaction_db  transaction_svc  "$TXN_DB_PASSWORD"
create_service_db fraud_db        fraud_svc        "$FRAUD_DB_PASSWORD"
create_service_db notification_db notification_svc "$NOTIFICATION_DB_PASSWORD"
create_service_db audit_db        audit_svc        "$AUDIT_DB_PASSWORD"
