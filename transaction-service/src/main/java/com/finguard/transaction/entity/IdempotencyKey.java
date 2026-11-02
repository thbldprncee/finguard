package com.finguard.transaction.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotNull
    @Column(nullable = false)
    private UUID userId;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String idempotencyKey;

    @NotNull
    @Pattern(regexp = "^[0-9a-f]{64}$")
    @Column(nullable = false)
    private String requestHash;

    private UUID transactionId;

    @NotNull
    @Column(nullable = false)
    private Instant createdAt;
}
