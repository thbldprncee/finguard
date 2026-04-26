package com.finguard.audit.kafka;

import com.finguard.audit.entity.AuditEvent;
import com.finguard.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditEventRepository auditEventRepository;

    @KafkaListener(topics = {
            "audit.event.created",
            "transaction.created",
            "fraud.analysis.completed"
    }, groupId = "audit-service")
    @Transactional
    public void onEvent(Map<String, Object> payload) {
        String eventType = (String) payload.getOrDefault("eventType", "UNKNOWN");
        String aggregateId = (String) payload.getOrDefault("aggregateId",
                payload.getOrDefault("transactionId", "unknown").toString());
        String correlationId = (String) payload.get("correlationId");

        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .correlationId(correlationId)
                .build();

        auditEventRepository.save(event);
        log.debug("Stored audit event: type={}, aggregateId={}", eventType, aggregateId);
    }
}
