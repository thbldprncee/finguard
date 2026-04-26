package com.finguard.transaction.service;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.IdempotencyKey;
import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.kafka.TransactionEventProducer;
import com.finguard.transaction.repository.IdempotencyKeyRepository;
import com.finguard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock TransactionEventProducer eventProducer;

    @InjectMocks TransactionService transactionService;

    private CreateTransactionRequest sampleRequest() {
        return new CreateTransactionRequest(
                "ACC-001", "ACC-002",
                new BigDecimal("500.00"), "USD", "US",
                "RETAIL", "CARD", "192.168.1.1", "device-123"
        );
    }

    @Test
    void createTransaction_savesAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        Transaction saved = Transaction.builder()
                .id(UUID.randomUUID())
                .externalTransactionId("ext-123")
                .userId(userId)
                .sourceAccount("ACC-001")
                .targetAccount("ACC-002")
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .country("US")
                .paymentMethod("CARD")
                .status(Transaction.Status.PENDING)
                .build();

        when(transactionRepository.save(any())).thenReturn(saved);

        var response = transactionService.createTransaction(sampleRequest(), userId, null, "corr-1");

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.userId()).isEqualTo(userId);
        verify(eventProducer).publishTransactionCreated(any(), eq("corr-1"));
    }

    @Test
    void createTransaction_returnsExisting_whenIdempotencyKeyAlreadyUsed() {
        UUID userId = UUID.randomUUID();
        UUID existingTxId = UUID.randomUUID();
        String ikey = "ikey-abc";

        Transaction existingTx = Transaction.builder()
                .id(existingTxId).userId(userId).externalTransactionId("ext-1")
                .sourceAccount("A").targetAccount("B").amount(BigDecimal.TEN)
                .currency("USD").country("US").paymentMethod("CARD")
                .status(Transaction.Status.PENDING).build();

        when(idempotencyKeyRepository.findByIdempotencyKey(ikey))
                .thenReturn(Optional.of(IdempotencyKey.builder().idempotencyKey(ikey).transactionId(existingTxId).build()));
        when(transactionRepository.findById(existingTxId)).thenReturn(Optional.of(existingTx));

        var response = transactionService.createTransaction(sampleRequest(), userId, ikey, null);

        assertThat(response.id()).isEqualTo(existingTxId);
        verify(transactionRepository, never()).save(any());
        verify(eventProducer, never()).publishTransactionCreated(any(), any());
    }
}
