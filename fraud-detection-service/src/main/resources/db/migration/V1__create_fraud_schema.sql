CREATE SCHEMA IF NOT EXISTS fraud;

CREATE TABLE fraud.fraud_rules (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    weight      INT         NOT NULL DEFAULT 10,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE fraud.fraud_analyses (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID        NOT NULL UNIQUE,
    risk_score      INT         NOT NULL,
    decision        VARCHAR(20) NOT NULL,
    triggered_rules TEXT[],
    explanation     TEXT,
    analyzed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_analyses_transaction_id ON fraud.fraud_analyses(transaction_id);
CREATE INDEX idx_fraud_analyses_decision       ON fraud.fraud_analyses(decision);
CREATE INDEX idx_fraud_analyses_analyzed_at    ON fraud.fraud_analyses(analyzed_at DESC);
