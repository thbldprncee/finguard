package com.finguard.transaction.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository repository;
    /** Absent when the relay is disabled (finguard.outbox.relay-enabled=false). */
    private final ObjectProvider<OutboxTrigger> trigger;

    /**
     * Must run inside the business transaction: the event is committed or rolled back
     * together with the state change, so neither can exist without the other.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(OutboxMessage message) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        Map<String, Object> payload = new LinkedHashMap<>(message.payload());
        payload.put("eventId", eventId.toString());
        payload.put("eventType", message.eventType());
        payload.put("correlationId", message.correlationId());
        payload.putIfAbsent("occurredAt", now.toString());

        repository.save(OutboxEvent.builder()
                .id(eventId)
                .aggregateType(message.aggregateType())
                .aggregateId(message.aggregateId())
                .eventType(message.eventType())
                .topic(message.topic())
                .messageKey(message.key())
                .payload(payload)
                .correlationId(message.correlationId())
                .createdAt(now)
                .nextAttemptAt(now)
                .attempts(0)
                .build());

        // Publish as soon as the business transaction commits; never before, or a rolled-back
        // change could be announced. Several events in one transaction coalesce into one run.
        OutboxTrigger t = trigger.getIfAvailable();
        if (t != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    t.requestPublish();
                }
            });
        }
        return eventId;
    }
}
