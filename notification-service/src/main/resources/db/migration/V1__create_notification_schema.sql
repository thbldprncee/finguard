CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notifications (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    transaction_id UUID,
    type           VARCHAR(30) NOT NULL,
    message        TEXT        NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SENT',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_user_id       ON notification.notifications(user_id);
CREATE INDEX idx_notif_transaction_id ON notification.notifications(transaction_id);
