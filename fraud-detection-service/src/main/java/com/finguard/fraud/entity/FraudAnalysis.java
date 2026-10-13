package com.finguard.fraud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_analyses", schema = "fraud")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique: one analysis per transaction, which also makes re-delivered events no-ops. */
    @Column(nullable = false, unique = true)
    private UUID transactionId;

    /** Owner of the transaction, used to restrict what a USER may read. */
    private UUID userId;

    /** When the transaction was created; history rules look back from this, not from analysis time. */
    private Instant transactionOccurredAt;

    @Column(nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(columnDefinition = "TEXT[]")
    private String[] triggeredRules;

    /** Rules that could not be evaluated; their presence escalates the decision. */
    @Column(columnDefinition = "TEXT[]")
    private String[] failedRules;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant analyzedAt;

    public enum Decision {
        APPROVED, REVIEW_REQUIRED, BLOCKED;

        public Decision atLeast(Decision other) {
            return ordinal() >= other.ordinal() ? this : other;
        }
    }
}
