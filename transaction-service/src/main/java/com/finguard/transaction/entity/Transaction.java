package com.finguard.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "txn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String externalTransactionId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String sourceAccount;

    @Column(nullable = false)
    private String targetAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 3)
    private String country;

    private String merchantCategory;

    @Column(nullable = false)
    private String paymentMethod;

    private String ipAddress;

    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * Applies an automated fraud decision; returns false when the current state ignores it.
     * Duplicate or out-of-order Kafka events are therefore harmless.
     */
    public boolean applyFraudDecision(Status decision) {
        Status next = status.onFraudDecision(decision);
        if (next == status) {
            return false;
        }
        status = next;
        return true;
    }

    /**
     * State pattern: each status decides how it reacts to an event instead of callers
     * scattering "if status == PENDING" checks.
     * <ul>
     *   <li>PENDING accepts the fraud engine's decision;</li>
     *   <li>REVIEW_REQUIRED waits for an analyst, a (replayed) automated decision cannot change it;</li>
     *   <li>APPROVED and BLOCKED are final.</li>
     * </ul>
     */
    public enum Status {
        PENDING {
            @Override
            Status onFraudDecision(Status decision) {
                return requireDecision(decision);
            }
        },
        REVIEW_REQUIRED,
        APPROVED,
        BLOCKED;

        /** Default for every state except PENDING: ignore automated decisions. */
        Status onFraudDecision(Status decision) {
            requireDecision(decision);
            return this;
        }

        public boolean isFinal() {
            return this == APPROVED || this == BLOCKED;
        }

        private static Status requireDecision(Status decision) {
            if (decision == null || decision == PENDING) {
                throw new IllegalArgumentException("Not a fraud decision: " + decision);
            }
            return decision;
        }
    }
}
