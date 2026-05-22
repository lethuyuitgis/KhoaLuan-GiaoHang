-- Enforce the invariant "a shipper has at most one STARTED assignment at a time".
-- The Live Location handler routes pings to the shipper's STARTED assignment, so two STARTED
-- rows for the same shipper would silently send GPS to whichever one the query returned first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_assignment_shipper_started
    ON delivery_assignment(shipper_id)
    WHERE status = 'STARTED';
