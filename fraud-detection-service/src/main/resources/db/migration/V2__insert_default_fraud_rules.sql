INSERT INTO fraud.fraud_rules (code, name, description, enabled, weight) VALUES
('HIGH_AMOUNT',          'High Amount',                'Transaction exceeds the configured amount threshold',              true, 30),
('VELOCITY',             'Velocity Check',             'User made too many transactions in a short time window',           true, 25),
('COUNTRY_CHANGE',       'Country Change',             'Transaction country differs from user recent transaction country',  true, 20),
('NIGHT_ACTIVITY',       'Night Activity',             'Transaction occurred during unusual hours (midnight to 5 AM)',     true, 15),
('SUSPICIOUS_MERCHANT',  'Suspicious Merchant',        'Transaction merchant category is considered high-risk',            true, 20),
('NEW_DEVICE',           'New Device',                 'Transaction came from a previously unseen device ID',             true, 15),
('REPEATED_BLOCKED',     'Repeated Blocked Activity',  'User recently had blocked transactions',                          true, 25)
ON CONFLICT (code) DO NOTHING;
