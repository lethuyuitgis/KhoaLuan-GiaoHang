-- Baseline migration for P0.
-- Future plans add tables: P1 (auth), P2 (order), P5 (delivery), P7 (payment).
-- This file establishes Flyway version tracking and creates a meta table.

CREATE TABLE app_meta (
    key VARCHAR(64) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_meta (key, value) VALUES
    ('schema_version', 'p0-baseline'),
    ('initialized_at', NOW()::TEXT);
