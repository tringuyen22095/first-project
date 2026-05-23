-- Create flyway user for DDL only
CREATE USER flyway WITH PASSWORD 'flyway_admin';
GRANT CONNECT ON DATABASE monolithic TO flyway;
GRANT USAGE, CREATE ON SCHEMA public TO flyway;

-- Create nene user for DML only
CREATE USER nene WITH PASSWORD '475963218';
GRANT CONNECT ON DATABASE monolithic TO nene;
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA public TO nene;

-- Automatically grant DML to nene user on any table/sequence flyway creates in future.
ALTER DEFAULT PRIVILEGES FOR USER flyway IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nene;

ALTER DEFAULT PRIVILEGES FOR USER flyway IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO nene;
