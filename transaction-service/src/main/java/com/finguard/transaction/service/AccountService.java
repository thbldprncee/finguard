package com.finguard.transaction.service;

import com.finguard.transaction.config.TransactionProperties;
import com.finguard.transaction.dto.AccountResponse;
import com.finguard.transaction.entity.Account;
import com.finguard.transaction.exception.BusinessRuleException;
import com.finguard.transaction.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final TransactionProperties properties;

    @Transactional(readOnly = true)
    public List<AccountResponse> listOwn(UUID userId) {
        return accountRepository.findByUserIdOrderByCreatedAt(userId).stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse open(UUID userId, String currency) {
        if (!properties.supportedCurrencies().contains(currency)) {
            throw new BusinessRuleException("Unsupported currency: " + currency);
        }
        if (accountRepository.countByUserId(userId) >= properties.maxAccountsPerUser()) {
            throw new BusinessRuleException("Account limit reached");
        }
        Account account = accountRepository.save(Account.builder()
                .accountNumber(newAccountNumber())
                .userId(userId)
                .currency(currency)
                .status(Account.Status.ACTIVE)
                .build());
        return AccountResponse.from(account);
    }

    /**
     * The same error whether the account does not exist or belongs to someone else,
     * so the endpoint cannot be used to discover other users' account numbers.
     */
    @Transactional(readOnly = true)
    public Account requireOwnedActive(String accountNumber, UUID userId) {
        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, userId)
                .orElseThrow(() -> new BusinessRuleException("Source account not found"));
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessRuleException("Source account is not active");
        }
        return account;
    }

    private String newAccountNumber() {
        String number;
        do {
            StringBuilder sb = new StringBuilder("FG");
            for (int i = 0; i < 16; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            number = sb.toString();
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
