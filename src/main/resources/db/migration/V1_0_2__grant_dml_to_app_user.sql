-- Grant DML permissions to the app user (sa) on all tables created by the flyway user.
-- This ensures sa can SELECT/INSERT/UPDATE/DELETE on all migrated tables.

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sa;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sa;
