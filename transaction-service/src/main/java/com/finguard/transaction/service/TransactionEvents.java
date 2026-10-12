package com.finguard.transaction.service;

import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.outbox.OutboxMessage;
import com.finguard.transaction.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TransactionEvents {

    static final String TOPIC_CREATED = "transaction.created";

    private final OutboxWriter outboxWriter;

    /**
     * Keyed by userId, not transactionId: all events of one user land in one partition and are
     * processed in order, which the per-user fraud rules (velocity, country change) depend on.
     */
    public void created(Transaction tx, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", tx.getId().toString());
        payload.put("externalTransactionId", tx.getExternalTransactionId());
        payload.put("aggregateId", tx.getId().toString());
        payload.put("userId", tx.getUserId().toString());
        payload.put("sourceAccount", tx.getSourceAccount());
        payload.put("targetAccount", tx.getTargetAccount());
        // As a string: a JSON number would be read back as a double and lose precision.
        payload.put("amount", tx.getAmount().toPlainString());
        payload.put("currency", tx.getCurrency());
        payload.put("country", tx.getCountry());
        payload.put("merchantCategory", tx.getMerchantCategory());
        payload.put("paymentMethod", tx.getPaymentMethod());
        payload.put("ipAddress", tx.getIpAddress());
        payload.put("deviceId", tx.getDeviceId());
        payload.put("occurredAt", (tx.getCreatedAt() != null ? tx.getCreatedAt() : Instant.now()).toString());

        outboxWriter.enqueue(new OutboxMessage(
                TOPIC_CREATED, "TRANSACTION", tx.getId().toString(), tx.getUserId().toString(),
                "TRANSACTION_CREATED", payload, correlationId));
    }
}
