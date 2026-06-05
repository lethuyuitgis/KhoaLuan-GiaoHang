-- V3__bot.sql — bot module tables
-- Owns: processed_update (idempotency), conversation_state (FSM)

CREATE TABLE processed_update (
    update_id       BIGINT PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cron sẽ xóa record cũ > 24h. Index hỗ trợ scan cleanup.
CREATE INDEX idx_processed_update_processed_at ON processed_update(processed_at);

CREATE TABLE conversation_state (
    telegram_user_id    BIGINT PRIMARY KEY REFERENCES telegram_user(id) ON DELETE CASCADE,
    state               VARCHAR(64) NOT NULL,
    data                JSONB,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TTL 30 phút, cron sẽ xóa stale state
CREATE INDEX idx_conversation_state_updated_at ON conversation_state(updated_at);
