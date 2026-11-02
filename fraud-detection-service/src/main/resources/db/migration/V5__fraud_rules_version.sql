-- Optimistic locking for rule administration: every update bumps the version, and an update
-- based on a stale version is rejected instead of silently overwriting another admin's change.
ALTER TABLE fraud.fraud_rules ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The weight limits are already enforced by the API; the database now enforces them too.
ALTER TABLE fraud.fraud_rules ADD CONSTRAINT chk_fraud_rules_weight CHECK (weight BETWEEN 1 AND 100);
