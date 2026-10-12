-- Accounts: a transaction may only debit an account owned by the caller.
CREATE TABLE txn.accounts (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(34) NOT NULL UNIQUE,
    user_id        UUID        NOT NULL,
    currency       VARCHAR(3)  NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_accounts_user_id ON txn.accounts(user_id);

-- Idempotency keys are now scoped per user and remember a hash of the request.
-- Keys are short-lived retry tokens, so existing ones are dropped rather than migrated.
DROP TABLE txn.idempotency_keys;
CREATE TABLE txn.idempotency_keys (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    transaction_id  UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_idempotency_created ON txn.idempotency_keys(created_at);

DROP INDEX IF EXISTS txn.idx_txn_external_id; -- duplicated the UNIQUE constraint's index

CREATE TABLE txn.outbox_events (
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
CREATE INDEX idx_outbox_pending   ON txn.outbox_events(next_attempt_at, created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published ON txn.outbox_events(published_at) WHERE published_at IS NOT NULL;
