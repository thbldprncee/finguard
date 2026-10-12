package com.finguard.fraud.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository repository;

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
        return eventId;
    }
}
