package com.finguard.transaction.validation;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Account;

/**
 * One business check of a new transaction (Chain of Responsibility). Each link either rejects
 * the request with a {@link com.finguard.transaction.exception.BusinessRuleException} or lets it
 * pass to the next one. New checks are new beans; {@link TransactionValidator} does not change.
 * Spring orders the links by {@link org.springframework.core.annotation.Order}.
 */
public interface TransactionValidationRule {

    void validate(CreateTransactionRequest request, Account source);
}
