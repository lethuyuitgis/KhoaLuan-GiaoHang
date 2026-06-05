-- V2__auth.sql — auth module tables
-- Owns: telegram_user, user_role
-- Used by: bot (register user on /start), miniapp (role lookup), webadmin (role check)

CREATE TABLE telegram_user (
    id              BIGINT PRIMARY KEY,             -- = Telegram user ID
    username        VARCHAR(64),
    first_name      VARCHAR(128),
    last_name       VARCHAR(128),
    phone           VARCHAR(32),
    photo_url       TEXT,
    language_code   VARCHAR(8),
    is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telegram_user_username ON telegram_user(username) WHERE username IS NOT NULL;

CREATE TABLE user_role (
    id                  BIGSERIAL PRIMARY KEY,
    telegram_user_id    BIGINT NOT NULL REFERENCES telegram_user(id) ON DELETE CASCADE,
    role                VARCHAR(16) NOT NULL,           -- CUSTOMER | SHIPPER | SHOP_OWNER
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PENDING | BLOCKED
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_role UNIQUE (telegram_user_id, role)
);

CREATE INDEX idx_user_role_lookup ON user_role(telegram_user_id, status);
