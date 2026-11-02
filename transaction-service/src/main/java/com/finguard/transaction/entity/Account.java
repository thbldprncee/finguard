package com.finguard.transaction.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A customer account. Transactions may only debit an account owned by the caller. */
@Entity
@Table(name = "accounts", schema = "txn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 34)
    @Column(nullable = false, unique = true)
    private String accountNumber;

    @NotNull
    @Column(nullable = false)
    private UUID userId;

    @NotNull
    @Pattern(regexp = "^[A-Z]{3}$")
    @Column(nullable = false, length = 3)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public enum Status {
        ACTIVE, FROZEN
    }
}
