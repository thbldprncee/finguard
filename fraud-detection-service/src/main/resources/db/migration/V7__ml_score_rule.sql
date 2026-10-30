-- The ML model as one more weighted rule: triggered when its fraud probability reaches
-- finguard.fraud.ml.threshold. Disabled by default (shadow mode: the score is stored but does not
-- count); an admin enables it and tunes its weight like any other rule.
INSERT INTO fraud.fraud_rules (code, name, description, enabled, weight) VALUES
('ML_SCORE', 'ML Model', 'Fraud probability from the ML model reaches the configured threshold', false, 30)
ON CONFLICT (code) DO NOTHING;
