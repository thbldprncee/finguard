package com.finguard.audit.kafka;

import com.finguard.audit.entity.AuditEvent;
import com.finguard.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditEventRepository auditEventRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-audit-retry",
            dltTopicSuffix = "-audit-dlt",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = {
            "audit.event.created",
            "transaction.created",
            "fraud.analysis.completed"
    }, groupId = "audit-service")
    public void onEvent(Map<String, Object> payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(value = "X-Event-Id", required = false) String eventIdHeader,
                        @Header(value = "X-Event-Type", required = false) String eventTypeHeader,
                        @Header(value = "X-Correlation-Id", required = false) String correlationIdHeader) {
        UUID eventId = parseUuid(eventIdHeader != null ? eventIdHeader : payload.get("eventId"));
        if (eventId != null && auditEventRepository.existsByEventId(eventId)) {
            log.debug("Audit event {} already stored, skipping duplicate", eventId);
            return;
        }

        String eventType = firstNonBlank(eventTypeHeader, payload.get("eventType"), topic);
        String aggregateId = firstNonBlank(payload.get("aggregateId"), payload.get("transactionId"), "unknown");
        String correlationId = firstNonBlank(correlationIdHeader, payload.get("correlationId"), null);

        try {
            auditEventRepository.save(AuditEvent.builder()
                    .eventId(eventId)
                    .eventType(truncate(eventType, 60))
                    .topic(topic)
                    .aggregateId(truncate(aggregateId, 100))
                    .payload(payload)
                    .correlationId(truncate(correlationId, 100))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate (retry topic vs main topic): the unique event_id already holds it.
            log.debug("Duplicate audit event {}", eventId);
        }
    }

    @DltHandler
    public void onDeadLetter(Map<String, Object> payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Audit event could not be stored and was moved to {}: {}", topic, payload);
    }

    private static UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstNonBlank(Object a, Object b, String fallback) {
        if (a != null && !a.toString().isBlank()) return a.toString();
        if (b != null && !b.toString().isBlank()) return b.toString();
        return fallback;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
