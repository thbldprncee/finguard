package com.finguard.transaction.dto;

import com.finguard.transaction.entity.Account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String accountNumber, String currency, String status, Instant createdAt) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getAccountNumber(), a.getCurrency(), a.getStatus().name(), a.getCreatedAt());
    }
}
