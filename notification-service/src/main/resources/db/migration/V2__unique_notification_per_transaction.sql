-- One notification of each type per transaction, even if Kafka delivers the event twice.
DELETE FROM notification.notifications a
USING notification.notifications b
WHERE a.transaction_id = b.transaction_id AND a.type = b.type AND a.created_at > b.created_at;

DROP INDEX IF EXISTS notification.idx_notif_transaction_id;
CREATE UNIQUE INDEX ux_notif_transaction_type ON notification.notifications(transaction_id, type);
