package com.finguard.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * IP address and device id are intentionally not part of the body: the fraud engine must not
 * score values the client picks. They come from X-Client-Ip (gateway) and X-Device-Id.
 * Length limits match the database columns, so oversized input is a 400, not a 500.
 */
public record CreateTransactionRequest(
        @NotBlank(message = "Source account is required")
        @Pattern(regexp = "^[A-Z0-9-]{4,34}$", message = "Invalid account number")
        String sourceAccount,

        @NotBlank(message = "Target account is required")
        @Pattern(regexp = "^[A-Z0-9-]{4,34}$", message = "Invalid account number")
        String targetAccount,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be an ISO 4217 code")
        String currency,

        @NotBlank(message = "Country is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be an ISO 3166-1 alpha-2 code")
        String country,

        @Pattern(regexp = "^[A-Z_]{0,50}$", message = "Invalid merchant category")
        String merchantCategory,

        @NotBlank(message = "Payment method is required")
        @Pattern(regexp = "^[A-Z_]{2,30}$", message = "Invalid payment method")
        String paymentMethod
) {}
