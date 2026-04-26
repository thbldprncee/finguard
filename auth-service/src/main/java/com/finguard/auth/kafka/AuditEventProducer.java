package com.finguard.auth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventProducer {

    private static final String TOPIC = "audit.event.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuditEvent(String eventType, String aggregateId, Map<String, Object> payload, String correlationId) {
        var event = Map.of(
                "eventType", eventType,
                "aggregateId", aggregateId,
                "payload", payload,
                "correlationId", correlationId != null ? correlationId : "",
                "occurredAt", Instant.now().toString()
        );
        kafkaTemplate.send(TOPIC, aggregateId, event);
        log.info("Published audit event: type={}, aggregateId={}", eventType, aggregateId);
    }
}
