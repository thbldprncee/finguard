package com.finguard.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Whether a transaction really was fraud: the training label for the ML model. */
@Entity
@Table(name = "fraud_labels", schema = "fraud")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudLabel {

    @Id
    private UUID transactionId;

    @Column(nullable = false)
    private boolean fraud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    /** Synthetic data only: which generated pattern the transaction belongs to. */
    private String scenario;

    private UUID labeledBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private Instant labeledAt;

    public enum Source {
        ANALYST, SYNTHETIC
    }
}
