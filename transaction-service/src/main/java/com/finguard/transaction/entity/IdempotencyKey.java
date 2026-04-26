package com.finguard.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", schema = "txn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID transactionId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
