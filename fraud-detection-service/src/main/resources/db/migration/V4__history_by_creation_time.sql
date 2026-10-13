-- History rules (REPEATED_BLOCKED) look back from the transaction's creation time.
ALTER TABLE fraud.fraud_analyses ADD COLUMN transaction_occurred_at TIMESTAMPTZ;
UPDATE fraud.fraud_analyses SET transaction_occurred_at = analyzed_at WHERE transaction_occurred_at IS NULL;
CREATE INDEX idx_fraud_analyses_user_decision_time
    ON fraud.fraud_analyses(user_id, decision, transaction_occurred_at);

-- Supports the per-key "oldest unpublished event" check of the outbox relay.
CREATE INDEX idx_outbox_key_pending ON fraud.outbox_events(message_key, created_at, id) WHERE published_at IS NULL;
