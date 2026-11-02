-- ML in shadow mode: the features the service saw at scoring time and the model's score. The
-- model is trained on these stored features, so training and serving use identical inputs.
-- All nullable: analyses before this migration and analyses without a model have none.
ALTER TABLE fraud.fraud_analyses ADD COLUMN features JSONB;
ALTER TABLE fraud.fraud_analyses ADD COLUMN ml_score DOUBLE PRECISION;
ALTER TABLE fraud.fraud_analyses ADD COLUMN ml_model_version VARCHAR(64);

-- Ground truth for training: whether a transaction really was fraud. Set by an analyst
-- (ANALYST) or by the synthetic data generator (SYNTHETIC); the decision is not a label, the
-- model would only learn to imitate the rules.
CREATE TABLE fraud.fraud_labels (
    transaction_id  UUID          PRIMARY KEY,
    fraud           BOOLEAN       NOT NULL,
    source          VARCHAR(20)   NOT NULL CHECK (source IN ('ANALYST', 'SYNTHETIC')),
    scenario        VARCHAR(50),
    labeled_by      UUID,
    note            TEXT,
    labeled_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
