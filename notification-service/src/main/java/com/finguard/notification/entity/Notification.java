package com.finguard.notification.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(nullable = false)
    private UUID userId;

    private UUID transactionId;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false)
    private String type;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false)
    private String status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
