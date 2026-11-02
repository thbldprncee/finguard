package com.finguard.fraud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_rules", schema = "fraud")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Min(1)
    @Max(100)
    @Column(nullable = false)
    private int weight;

    /**
     * Optimistic lock. Hibernate adds "AND version = ?" to every UPDATE and increments it;
     * an update based on a stale copy matches no row and fails instead of overwriting.
     * Clients get it as the ETag and send it back in If-Match.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
