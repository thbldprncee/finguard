package com.finguard.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Keys are scoped per user: another user sending the same key gets their own transaction,
 * never somebody else's. requestHash detects a key reused for a different request.
 */
@Entity
@Table(name = "idempotency_keys", schema = "txn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    private UUID transactionId;

    @Column(nullable = false)
    private Instant createdAt;
}
