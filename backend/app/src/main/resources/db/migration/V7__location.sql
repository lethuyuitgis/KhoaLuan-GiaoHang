-- V7__location.sql — Live Location ping storage
-- Owns: location_ping

CREATE TABLE location_ping (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   UUID NOT NULL REFERENCES delivery_assignment(id) ON DELETE CASCADE,
    lat             NUMERIC(10, 7) NOT NULL,
    lng             NUMERIC(10, 7) NOT NULL,
    accuracy        NUMERIC(8, 2),
    heading         NUMERIC(5, 2),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_location_ping_assignment_time ON location_ping(assignment_id, recorded_at DESC);
CREATE INDEX idx_location_ping_recorded_at ON location_ping(recorded_at);
