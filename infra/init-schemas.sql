-- init-schemas.sql
-- Runs automatically when the postgres container starts for the first time.
-- Creates schemas and scoped DB users for each service.
-- Mirrors the production setup in Azure PostgreSQL Flexible Server.

-- Create schemas
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS journal;

-- User Service DB user — access to users schema only
CREATE USER user_svc WITH PASSWORD 'user_svc_password';
GRANT USAGE ON SCHEMA users TO user_svc;
GRANT CREATE ON SCHEMA users TO user_svc;  -- required for Flyway migrations
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA users TO user_svc;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA users TO user_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA users GRANT ALL ON TABLES TO user_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA users GRANT ALL ON SEQUENCES TO user_svc;

-- Journal Service DB user — access to journal schema only
CREATE USER journal_svc WITH PASSWORD 'journal_svc_password';
GRANT USAGE ON SCHEMA journal TO journal_svc;
GRANT CREATE ON SCHEMA journal TO journal_svc;  -- required for Flyway migrations
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA journal TO journal_svc;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA journal TO journal_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA journal GRANT ALL ON TABLES TO journal_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA journal GRANT ALL ON SEQUENCES TO journal_svc;
