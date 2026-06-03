-- V5__admin.sql — admin auth tables (Web Admin)
-- Owns: admin_user, refresh_token

CREATE TABLE admin_user (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(128),
    telegram_user_id    BIGINT REFERENCES telegram_user(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_user_active ON admin_user(is_active) WHERE is_active = TRUE;

CREATE TABLE refresh_token (
    id                  UUID PRIMARY KEY,
    admin_user_id       BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    token_hash          VARCHAR(255) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user ON refresh_token(admin_user_id);
CREATE INDEX idx_refresh_token_expiry ON refresh_token(expires_at) WHERE revoked = FALSE;

-- Seed default admin: admin@shop.local / admin123 (BCrypt-10 hash)
-- Hash generated via htpasswd -bnBC 10 (compatible with Spring Security BCryptPasswordEncoder)
INSERT INTO admin_user(email, password_hash, full_name)
VALUES (
    'admin@shop.local',
    '$2a$10$8tbM0mvZZFQuz9KVhA6lOOZQfM2GxHIgmTWXbcVQ2ml353wyiYktO',
    'Shop Owner'
);
