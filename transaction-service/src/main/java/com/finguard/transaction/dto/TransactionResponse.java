package com.finguard.transaction.dto;

import com.finguard.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String externalTransactionId,
        UUID userId,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        String currency,
        String country,
        String merchantCategory,
        String paymentMethod,
        String ipAddress,
        String deviceId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getExternalTransactionId(), tx.getUserId(),
                tx.getSourceAccount(), tx.getTargetAccount(), tx.getAmount(),
                tx.getCurrency(), tx.getCountry(), tx.getMerchantCategory(),
                tx.getPaymentMethod(), tx.getIpAddress(), tx.getDeviceId(),
                tx.getStatus().name(), tx.getCreatedAt(), tx.getUpdatedAt()
        );
    }
}
