package com.finguard.transaction.outbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A domain event stored in the same database transaction as the state change that produced it.
 * {@link OutboxRelay} publishes it to Kafka afterwards (at-least-once).
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false)
    private String aggregateType;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String aggregateId;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false)
    private String eventType;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String topic;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String messageKey;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Size(max = 64)
    private String correlationId;

    @NotNull
    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @PositiveOrZero
    @Column(nullable = false)
    private int attempts;

    @NotNull
    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Size(max = 1000)
    private String lastError;
}
