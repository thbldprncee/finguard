ALTER TABLE audit.audit_events ADD COLUMN event_id UUID;
ALTER TABLE audit.audit_events ADD COLUMN topic VARCHAR(100);
-- Producer event id: the same event delivered twice is stored once.
CREATE UNIQUE INDEX ux_audit_event_id ON audit.audit_events(event_id);
