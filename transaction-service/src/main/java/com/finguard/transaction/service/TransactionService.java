package com.finguard.transaction.service;

import com.finguard.transaction.config.TransactionProperties;
import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.dto.TransactionResponse;
import com.finguard.transaction.entity.Account;
import com.finguard.transaction.entity.IdempotencyKey;
import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.event.TransactionCreated;
import com.finguard.transaction.exception.IdempotencyKeyReuseException;
import com.finguard.transaction.repository.IdempotencyKeyRepository;
import com.finguard.transaction.repository.TransactionRepository;
import com.finguard.transaction.security.AuthenticatedUser;
import com.finguard.transaction.security.RequestMetadata;
import com.finguard.transaction.validation.TransactionValidator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AccountService accountService;
    private final TransactionValidator validator;
    private final ApplicationEventPublisher events;
    private final TransactionProperties properties;

    /** @param replayed true when an earlier result was returned for the same Idempotency-Key */
    public record CreateResult(TransactionResponse transaction, boolean replayed) {}

    /**
     * The transaction row, the idempotency key and the outbox event are written in one database
     * transaction: either all three are committed or none. The Kafka publish happens later from
     * the outbox, so a broker outage cannot leave a transaction that the fraud engine never sees.
     * <p>
     * The idempotency key is handled before any validation: a retry returns the original result
     * even if the account was frozen in between. A rejected request rolls back its key claim,
     * so the client may retry after fixing the problem.
     */
    @Transactional
    public CreateResult createTransaction(CreateTransactionRequest request, UUID userId, RequestMetadata meta) {
        String requestHash = requestHash(request);
        String key = meta.idempotencyKey();
        if (key != null) {
            int claimed = idempotencyKeyRepository.insertIfAbsent(UUID.randomUUID(), userId, key, requestHash, Instant.now());
            if (claimed == 0) {
                return replay(userId, key, requestHash);
            }
        }

        Account source = accountService.requireOwnedActive(request.sourceAccount(), userId);
        validator.validate(request, source);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .externalTransactionId(UUID.randomUUID().toString())
                .userId(userId)
                .sourceAccount(source.getAccountNumber())
                .targetAccount(request.targetAccount())
                .amount(request.amount())
                .currency(request.currency())
                .country(request.country())
                .merchantCategory(blankToNull(request.merchantCategory()))
                .paymentMethod(request.paymentMethod())
                .ipAddress(meta.clientIp())
                .deviceId(meta.deviceId())
                .status(Transaction.Status.PENDING)
                .build());

        if (key != null) {
            idempotencyKeyRepository.attachTransaction(userId, key, transaction.getId());
        }
        events.publishEvent(new TransactionCreated(transaction, meta.correlationId()));
        log.info("Created transaction: id={}, userId={}", transaction.getId(), userId);

        return new CreateResult(TransactionResponse.from(transaction), false);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(AuthenticatedUser user, Pageable pageable) {
        Page<Transaction> page = user.isStaff()
                ? transactionRepository.findAll(pageable)
                : transactionRepository.findByUserId(user.userId(), pageable);
        return page.map(TransactionResponse::from);
    }

    /** Someone else's transaction answers 404, like a missing one, to avoid confirming that the id exists. */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId, AuthenticatedUser user) {
        return transactionRepository.findById(transactionId)
                .filter(tx -> user.isStaff() || tx.getUserId().equals(user.userId()))
                .map(TransactionResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    @Transactional
    public void deleteExpiredIdempotencyKeys() {
        int deleted = idempotencyKeyRepository.deleteCreatedBefore(Instant.now().minus(properties.idempotencyKeyTtl()));
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted);
        }
    }

    private CreateResult replay(UUID userId, String key, String requestHash) {
        IdempotencyKey existing = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key conflict but no row found"));
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException();
        }
        if (existing.getTransactionId() == null) {
            throw new IllegalStateException("Idempotency key committed without a transaction");
        }
        Transaction tx = transactionRepository.findById(existing.getTransactionId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record found but transaction missing"));
        log.info("Idempotent replay: userId={}, txId={}", userId, tx.getId());
        return new CreateResult(TransactionResponse.from(tx), true);
    }

    static String requestHash(CreateTransactionRequest r) {
        String canonical = String.join("|",
                r.sourceAccount(), r.targetAccount(), r.amount().stripTrailingZeros().toPlainString(),
                r.currency(), r.country(), Objects.toString(blankToNull(r.merchantCategory()), ""), r.paymentMethod());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
