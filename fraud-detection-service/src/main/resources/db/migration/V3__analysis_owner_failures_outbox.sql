ALTER TABLE fraud.fraud_analyses ADD COLUMN user_id UUID;
ALTER TABLE fraud.fraud_analyses ADD COLUMN failed_rules TEXT[];
CREATE INDEX idx_fraud_analyses_user_id ON fraud.fraud_analyses(user_id);
DROP INDEX IF EXISTS fraud.idx_fraud_analyses_transaction_id; -- duplicated the UNIQUE constraint's index

-- Weights are now applied from this table (previously the scores were hard-coded in yml).
INSERT INTO fraud.fraud_rules (code, name, description, enabled, weight) VALUES
('STRUCTURING', 'Structuring', 'Several transactions below the high-amount threshold add up to more than it', true, 25)
ON CONFLICT (code) DO NOTHING;

UPDATE fraud.fraud_rules
SET description = 'Transaction from a previously unseen device ID, or without a device ID'
WHERE code = 'NEW_DEVICE';

CREATE TABLE fraud.outbox_events (
    id              UUID          PRIMARY KEY,
    aggregate_type  VARCHAR(50)   NOT NULL,
    aggregate_id    VARCHAR(255)  NOT NULL,
    event_type      VARCHAR(60)   NOT NULL,
    topic           VARCHAR(100)  NOT NULL,
    message_key     VARCHAR(255)  NOT NULL,
    payload         JSONB         NOT NULL,
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    last_error      VARCHAR(1000)
);
CREATE INDEX idx_outbox_pending   ON fraud.outbox_events(next_attempt_at, created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published ON fraud.outbox_events(published_at) WHERE published_at IS NOT NULL;
