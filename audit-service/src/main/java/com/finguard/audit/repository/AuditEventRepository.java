package com.finguard.audit.repository;

import com.finguard.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);

    Page<AuditEvent> findByAggregateId(String aggregateId, Pageable pageable);

    Page<AuditEvent> findByEventTypeAndAggregateId(String eventType, String aggregateId, Pageable pageable);

    boolean existsByEventId(UUID eventId);
}
