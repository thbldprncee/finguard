package com.finguard.audit.service;

import com.finguard.audit.dto.AuditEventResponse;
import com.finguard.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> getEvents(String eventType, String aggregateId, Pageable pageable) {
        boolean byType = eventType != null && !eventType.isBlank();
        boolean byAggregate = aggregateId != null && !aggregateId.isBlank();
        if (byType && byAggregate) {
            return auditEventRepository.findByEventTypeAndAggregateId(eventType, aggregateId, pageable).map(AuditEventResponse::from);
        }
        if (byType) {
            return auditEventRepository.findByEventType(eventType, pageable).map(AuditEventResponse::from);
        }
        if (byAggregate) {
            return auditEventRepository.findByAggregateId(aggregateId, pageable).map(AuditEventResponse::from);
        }
        return auditEventRepository.findAll(pageable).map(AuditEventResponse::from);
    }
}
