package com.finguard.transaction.service;

import com.finguard.transaction.config.TransactionProperties;
import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Account;
import com.finguard.transaction.entity.IdempotencyKey;
import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.event.TransactionCreated;
import com.finguard.transaction.exception.BusinessRuleException;
import com.finguard.transaction.exception.IdempotencyKeyReuseException;
import com.finguard.transaction.repository.IdempotencyKeyRepository;
import com.finguard.transaction.repository.TransactionRepository;
import com.finguard.transaction.security.AuthenticatedUser;
import com.finguard.transaction.security.RequestMetadata;
import com.finguard.transaction.validation.TransactionValidator;
import com.finguard.transaction.validation.ValidationRules;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock AccountService accountService;
    @Mock ApplicationEventPublisher events;

    TransactionService transactionService;

    private final UUID userId = UUID.randomUUID();
    private final Account account = Account.builder().id(UUID.randomUUID()).accountNumber("FG0000000000000001")
            .userId(userId).currency("USD").status(Account.Status.ACTIVE).build();

    @BeforeEach
    void setUp() {
        var properties = new TransactionProperties(Set.of("USD", "EUR"), new BigDecimal("1000000"), Duration.ofHours(24), 5);
        var validator = new TransactionValidator(List.of(
                new ValidationRules.SupportedCurrency(properties),
                new ValidationRules.CurrencyMatchesAccount(),
                new ValidationRules.AmountWithinLimit(properties),
                new ValidationRules.DistinctAccounts()));
        transactionService = new TransactionService(transactionRepository, idempotencyKeyRepository,
                accountService, validator, events, properties);
        when(accountService.requireOwnedActive("FG0000000000000001", userId)).thenReturn(account);
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });
    }

    private CreateTransactionRequest request(String amount) {
        return new CreateTransactionRequest("FG0000000000000001", "ACC-002", new BigDecimal(amount),
                "USD", "US", "RETAIL", "CARD");
    }

    private RequestMetadata meta(String key) {
        return new RequestMetadata(key, "corr-1", "203.0.113.5", "device-12345678");
    }

    @Test
    void create_savesWithServerSideIpAndDevice_andWritesOutboxEvent() {
        var result = transactionService.createTransaction(request("500.00"), userId, meta(null));

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(saved.getValue().getDeviceId()).isEqualTo("device-12345678");
        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().status()).isEqualTo("PENDING");
        verify(events).publishEvent(argThat((Object e) -> e instanceof TransactionCreated tc && "corr-1".equals(tc.correlationId())));
    }

    @Test
    void create_claimsIdempotencyKey_andAttachesTransaction() {
        when(idempotencyKeyRepository.insertIfAbsent(any(), eq(userId), eq("key-12345678"), any(), any())).thenReturn(1);

        var result = transactionService.createTransaction(request("10"), userId, meta("key-12345678"));

        verify(idempotencyKeyRepository).attachTransaction(userId, "key-12345678", result.transaction().id());
    }

    @Test
    void create_replaysExistingTransaction_whenKeyAlreadyClaimed() {
        UUID existingTxId = UUID.randomUUID();
        Transaction existing = Transaction.builder().id(existingTxId).userId(userId).externalTransactionId("e")
                .sourceAccount("FG0000000000000001").targetAccount("ACC-002").amount(BigDecimal.TEN)
                .currency("USD").country("US").paymentMethod("CARD").status(Transaction.Status.APPROVED).build();
        String hash = TransactionService.requestHash(request("10"));
        when(idempotencyKeyRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);
        when(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, "key-12345678"))
                .thenReturn(Optional.of(IdempotencyKey.builder().userId(userId).idempotencyKey("key-12345678")
                        .requestHash(hash).transactionId(existingTxId).build()));
        when(transactionRepository.findById(existingTxId)).thenReturn(Optional.of(existing));

        var result = transactionService.createTransaction(request("10.00"), userId, meta("key-12345678"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction().id()).isEqualTo(existingTxId);
        verify(transactionRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void replay_doesNotRevalidate_soAFrozenAccountStillGetsTheOriginalResult() {
        UUID existingTxId = UUID.randomUUID();
        Transaction existing = Transaction.builder().id(existingTxId).userId(userId).externalTransactionId("e")
                .sourceAccount("FG0000000000000001").targetAccount("ACC-002").amount(BigDecimal.TEN)
                .currency("USD").country("US").paymentMethod("CARD").status(Transaction.Status.PENDING).build();
        when(idempotencyKeyRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);
        when(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, "key-12345678"))
                .thenReturn(Optional.of(IdempotencyKey.builder().requestHash(TransactionService.requestHash(request("10")))
                        .transactionId(existingTxId).build()));
        when(transactionRepository.findById(existingTxId)).thenReturn(Optional.of(existing));
        when(accountService.requireOwnedActive(any(), any())).thenThrow(new BusinessRuleException("Source account is not active"));

        var result = transactionService.createTransaction(request("10"), userId, meta("key-12345678"));

        assertThat(result.replayed()).isTrue();
        verify(accountService, never()).requireOwnedActive(any(), any());
    }

    @Test
    void create_rejectsKeyReuse_withDifferentPayload() {
        when(idempotencyKeyRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);
        when(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, "key-12345678"))
                .thenReturn(Optional.of(IdempotencyKey.builder().requestHash("other").transactionId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> transactionService.createTransaction(request("10"), userId, meta("key-12345678")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    @Test
    void create_rejectsCurrencyMismatchWithAccount() {
        var eur = new CreateTransactionRequest("FG0000000000000001", "ACC-002", BigDecimal.TEN, "EUR", "US", null, "CARD");

        assertThatThrownBy(() -> transactionService.createTransaction(eur, userId, meta(null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_rejectsAmountAboveLimit() {
        assertThatThrownBy(() -> transactionService.createTransaction(request("1000000.01"), userId, meta(null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_rejectsAccountOfAnotherUser() {
        UUID attacker = UUID.randomUUID();
        when(accountService.requireOwnedActive("FG0000000000000001", attacker))
                .thenThrow(new BusinessRuleException("Source account not found"));

        assertThatThrownBy(() -> transactionService.createTransaction(request("10"), attacker, meta(null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void get_hidesOtherUsersTransaction_as404() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(Transaction.builder().id(txId)
                .userId(UUID.randomUUID()).status(Transaction.Status.PENDING).build()));

        assertThatThrownBy(() -> transactionService.getTransaction(txId, new AuthenticatedUser(userId, "u@e.com", "USER")))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
