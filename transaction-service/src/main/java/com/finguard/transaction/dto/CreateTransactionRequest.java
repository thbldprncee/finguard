package com.finguard.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotBlank(message = "Source account is required")
        String sourceAccount,

        @NotBlank(message = "Target account is required")
        String targetAccount,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
        String currency,

        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 3, message = "Country must be ISO 2 or 3 letter code")
        String country,

        String merchantCategory,

        @NotBlank(message = "Payment method is required")
        String paymentMethod,

        String ipAddress,

        String deviceId
) {}
