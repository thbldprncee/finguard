package com.finguard.fraud.outbox;

import java.util.Map;

/**
 * @param key Kafka message key; events with the same key keep their relative order.
 */
public record OutboxMessage(
        String topic,
        String aggregateType,
        String aggregateId,
        String key,
        String eventType,
        Map<String, Object> payload,
        String correlationId
) {}
