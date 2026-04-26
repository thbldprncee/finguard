CREATE SCHEMA IF NOT EXISTS txn;

CREATE TABLE txn.transactions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    external_transaction_id VARCHAR(100) NOT NULL UNIQUE,
    user_id                 UUID        NOT NULL,
    source_account          VARCHAR(50) NOT NULL,
    target_account          VARCHAR(50) NOT NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency                VARCHAR(3)  NOT NULL,
    country                 VARCHAR(3)  NOT NULL,
    merchant_category       VARCHAR(50),
    payment_method          VARCHAR(30) NOT NULL,
    ip_address              VARCHAR(45),
    device_id               VARCHAR(100),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_user_id         ON txn.transactions(user_id);
CREATE INDEX idx_txn_status          ON txn.transactions(status);
CREATE INDEX idx_txn_created_at      ON txn.transactions(created_at DESC);
CREATE INDEX idx_txn_external_id     ON txn.transactions(external_transaction_id);
