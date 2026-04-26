CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type     VARCHAR(60) NOT NULL,
    aggregate_id   VARCHAR(100),
    payload        JSONB,
    correlation_id VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_event_type    ON audit.audit_events(event_type);
CREATE INDEX idx_audit_aggregate_id  ON audit.audit_events(aggregate_id);
CREATE INDEX idx_audit_created_at    ON audit.audit_events(created_at DESC);
