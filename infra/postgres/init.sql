-- Idempotent: PostgreSQL official image creates DB and USER from env vars,
-- this file is for additional setup (extensions, schemas).

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set timezone defaults
ALTER DATABASE shop_delivery SET timezone TO 'UTC';
