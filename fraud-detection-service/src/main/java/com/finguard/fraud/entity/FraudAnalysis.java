package com.finguard.fraud.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
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
    @NotNull
    @Column(nullable = false, unique = true)
    private UUID transactionId;

    /** Owner of the transaction, used to restrict what a USER may read. */
    private UUID userId;

    /** When the transaction was created; history rules look back from this, not from analysis time. */
    private Instant transactionOccurredAt;

    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private int riskScore;

    @NotNull
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

    /** Raw ML features at scoring time ({@link com.finguard.fraud.ml.MlFeatures}); training data. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> features;

    /** Fraud probability from the ML model (shadow mode: not part of the decision); null without a model. */
    private Double mlScore;

    private String mlModelVersion;

    public enum Decision {
        APPROVED, REVIEW_REQUIRED, BLOCKED;

        public Decision atLeast(Decision other) {
            return ordinal() >= other.ordinal() ? this : other;
        }
    }
}
