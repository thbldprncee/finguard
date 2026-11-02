package com.finguard.transaction.validation;

import com.finguard.transaction.controller.HeaderPatterns;
import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Account;
import com.finguard.transaction.entity.Transaction;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The declarative constraints on the API, the headers and the entities. */
class ConstraintsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private Transaction.TransactionBuilder validTransaction() {
        return Transaction.builder()
                .externalTransactionId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID())
                .sourceAccount("FG-0001")
                .targetAccount("ACC-EXTERNAL-1")
                .amount(new BigDecimal("10.50"))
                .currency("USD")
                .country("US")
                .paymentMethod("CARD")
                .status(Transaction.Status.PENDING);
    }

    @Test
    void createRequest_reportsEveryInvalidField() {
        var request = new CreateTransactionRequest("x", "", new BigDecimal("-1"), "usd", "USA", "bad category", null);

        assertThat(invalidFields(request)).containsExactlyInAnyOrder(
                "sourceAccount", "targetAccount", "amount", "currency", "country", "merchantCategory", "paymentMethod");
    }

    @Test
    void validEntity_passes() {
        assertThat(invalidFields(validTransaction().build())).isEmpty();
    }

    @Test
    void entity_rejectsValuesTheDatabaseColumnsCannotHold() {
        var tx = validTransaction()
                .userId(null)
                .amount(new BigDecimal("0"))
                .currency("usd")
                .deviceId("d".repeat(101))
                .ipAddress("1".repeat(46))
                .status(null)
                .build();

        assertThat(invalidFields(tx)).containsExactlyInAnyOrder("userId", "amount", "currency", "deviceId", "ipAddress", "status");
    }

    @Test
    void account_requiresOwnerAndIsoCurrency() {
        var account = Account.builder().accountNumber("FG-0001").currency("US").build();

        assertThat(invalidFields(account)).containsExactlyInAnyOrder("userId", "currency", "status");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abcdefgh", "key_with-dashes_123"})
    void idempotencyKey_acceptsEmptyOrWellFormed(String value) {
        assertThat(value).matches(HeaderPatterns.IDEMPOTENCY_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "has space in it", "semi;colon;key", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void idempotencyKey_rejectsMalformed(String value) {
        assertThat(value).doesNotMatch(HeaderPatterns.IDEMPOTENCY_KEY);
    }

    @Test
    void correlationAndClientIp_patterns() {
        assertThat("9f1c2b7e-1d2a-4c55-9a8e-3b2a1c0d9e8f").matches(HeaderPatterns.CORRELATION_ID);
        assertThat("x".repeat(65)).doesNotMatch(HeaderPatterns.CORRELATION_ID);
        assertThat("198.51.100.1").matches(HeaderPatterns.CLIENT_IP);
        assertThat("2001:db8::1").matches(HeaderPatterns.CLIENT_IP);
        assertThat("1.2.3.4, 5.6.7.8").doesNotMatch(HeaderPatterns.CLIENT_IP);
    }
}
