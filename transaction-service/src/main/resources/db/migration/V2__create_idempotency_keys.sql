CREATE TABLE txn.idempotency_keys (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    transaction_id  UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_idempotency_created ON txn.idempotency_keys(created_at);
