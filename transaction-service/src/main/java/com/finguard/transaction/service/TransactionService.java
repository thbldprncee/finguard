package com.finguard.transaction.service;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.dto.TransactionResponse;
import com.finguard.transaction.entity.IdempotencyKey;
import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.kafka.TransactionEventProducer;
import com.finguard.transaction.repository.IdempotencyKeyRepository;
import com.finguard.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionEventProducer eventProducer;

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request,
                                                 UUID userId,
                                                 String idempotencyKey,
                                                 String correlationId) {
        if (idempotencyKey != null) {
            var existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay: key={}, txId={}", idempotencyKey, existing.get().getTransactionId());
                return transactionRepository.findById(existing.get().getTransactionId())
                        .map(TransactionResponse::from)
                        .orElseThrow(() -> new IllegalStateException("Idempotency record found but transaction missing"));
            }
        }

        Transaction transaction = Transaction.builder()
                .externalTransactionId(UUID.randomUUID().toString())
                .userId(userId)
                .sourceAccount(request.sourceAccount())
                .targetAccount(request.targetAccount())
                .amount(request.amount())
                .currency(request.currency())
                .country(request.country())
                .merchantCategory(request.merchantCategory())
                .paymentMethod(request.paymentMethod())
                .ipAddress(request.ipAddress())
                .deviceId(request.deviceId())
                .status(Transaction.Status.PENDING)
                .build();

        transaction = transactionRepository.save(transaction);

        if (idempotencyKey != null) {
            idempotencyKeyRepository.save(
                    IdempotencyKey.builder()
                            .idempotencyKey(idempotencyKey)
                            .transactionId(transaction.getId())
                            .build()
            );
        }

        eventProducer.publishTransactionCreated(transaction, correlationId);
        log.info("Created transaction: id={}, userId={}", transaction.getId(), userId);

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(UUID userId, String role, Pageable pageable) {
        Page<Transaction> page = role.equals("ADMIN") || role.equals("ANALYST")
                ? transactionRepository.findAll(pageable)
                : transactionRepository.findByUserId(userId, pageable);
        return page.map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId, UUID userId, String role) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Transaction not found: " + transactionId));

        if (!role.equals("ADMIN") && !role.equals("ANALYST") && !tx.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        return TransactionResponse.from(tx);
    }
}
