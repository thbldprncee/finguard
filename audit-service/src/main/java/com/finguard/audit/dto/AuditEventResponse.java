package com.finguard.audit.dto;

import com.finguard.audit.entity.AuditEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        String eventType,
        String aggregateId,
        Map<String, Object> payload,
        String correlationId,
        Instant createdAt
) {
    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(), e.getEventType(), e.getAggregateId(),
                e.getPayload(), e.getCorrelationId(), e.getCreatedAt()
        );
    }
}
