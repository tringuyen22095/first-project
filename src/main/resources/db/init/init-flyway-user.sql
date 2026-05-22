-- Creates a dedicated DDL-only user for Flyway migrations.
-- This script runs on first PostgreSQL container initialization (docker-entrypoint-initdb.d).
-- The 'sa' app user retains only DML privileges (SELECT, INSERT, UPDATE, DELETE).

CREATE USER flyway WITH PASSWORD 'flyway_admin';

GRANT CONNECT ON DATABASE monolithic TO flyway;
GRANT USAGE, CREATE ON SCHEMA public TO flyway;

-- Automatically grant DML to the app user on any table/sequence flyway creates in future.
ALTER DEFAULT PRIVILEGES FOR USER flyway IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sa;

ALTER DEFAULT PRIVILEGES FOR USER flyway IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO sa;
