package com.finguard.transaction.validation;

import com.finguard.transaction.config.TransactionProperties;
import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Account;
import com.finguard.transaction.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The links of the transaction validation chain, in evaluation order. */
public final class ValidationRules {

    private ValidationRules() {}

    @Component
    @Order(10)
    @RequiredArgsConstructor
    public static class SupportedCurrency implements TransactionValidationRule {
        private final TransactionProperties properties;

        @Override
        public void validate(CreateTransactionRequest request, Account source) {
            if (!properties.supportedCurrencies().contains(request.currency())) {
                throw new BusinessRuleException("Unsupported currency: " + request.currency());
            }
        }
    }

    @Component
    @Order(20)
    public static class CurrencyMatchesAccount implements TransactionValidationRule {
        @Override
        public void validate(CreateTransactionRequest request, Account source) {
            if (!source.getCurrency().equals(request.currency())) {
                throw new BusinessRuleException("Currency does not match the source account currency " + source.getCurrency());
            }
        }
    }

    @Component
    @Order(30)
    @RequiredArgsConstructor
    public static class AmountWithinLimit implements TransactionValidationRule {
        private final TransactionProperties properties;

        @Override
        public void validate(CreateTransactionRequest request, Account source) {
            if (request.amount().compareTo(properties.maxAmount()) > 0) {
                throw new BusinessRuleException("Amount exceeds the maximum of " + properties.maxAmount().toPlainString());
            }
        }
    }

    @Component
    @Order(40)
    public static class DistinctAccounts implements TransactionValidationRule {
        @Override
        public void validate(CreateTransactionRequest request, Account source) {
            if (source.getAccountNumber().equals(request.targetAccount())) {
                throw new BusinessRuleException("Source and target accounts must differ");
            }
        }
    }
}
