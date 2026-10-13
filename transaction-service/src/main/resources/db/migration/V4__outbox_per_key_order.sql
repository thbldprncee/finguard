-- Supports the per-key "oldest unpublished event" check of the outbox relay.
CREATE INDEX idx_outbox_key_pending ON txn.outbox_events(message_key, created_at, id) WHERE published_at IS NULL;
