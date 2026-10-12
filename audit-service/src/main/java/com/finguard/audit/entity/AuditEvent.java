package com.finguard.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events", schema = "audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** X-Event-Id from the producer's outbox; unique, so a re-delivered event is stored once. */
    @Column(unique = true)
    private UUID eventId;

    @Column(nullable = false)
    private String eventType;

    private String topic;

    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    private String correlationId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
