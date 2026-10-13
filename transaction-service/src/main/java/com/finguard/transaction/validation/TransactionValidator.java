package com.finguard.transaction.validation;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs the validation chain; the first failing rule stops it. */
@Component
@RequiredArgsConstructor
public class TransactionValidator {

    private final List<TransactionValidationRule> rules;

    public void validate(CreateTransactionRequest request, Account source) {
        rules.forEach(rule -> rule.validate(request, source));
    }
}
