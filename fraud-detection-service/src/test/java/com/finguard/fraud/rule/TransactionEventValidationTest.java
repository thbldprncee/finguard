package com.finguard.fraud.rule;

import com.finguard.fraud.exception.MalformedEventException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionEventValidationTest {

    private Map<String, Object> valid() {
        Map<String, Object> e = new HashMap<>();
        e.put("transactionId", UUID.randomUUID().toString());
        e.put("userId", UUID.randomUUID().toString());
        e.put("amount", "150.25");
        e.put("currency", "USD");
        e.put("country", "US");
        e.put("merchantCategory", "RETAIL");
        e.put("paymentMethod", "CARD");
        e.put("deviceId", "device-12345678");
        e.put("ipAddress", "198.51.100.7");
        e.put("occurredAt", "2026-05-01T12:00:00Z");
        return e;
    }

    @Test
    void validEvent_isAccepted() {
        TransactionEvent event = TransactionEvent.from(valid());
        assertThat(event.amount()).isEqualByComparingTo("150.25");
        assertThat(event.country()).isEqualTo("US");
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "amount, 0",
            "amount, -5",
            "amount, 0.00001",
            "currency, usd",
            "currency, DOLLARS",
            "country, usa",
            "country, U",
    })
    void outOfRangeValues_areMalformed_andNameTheField(String field, String value) {
        Map<String, Object> event = valid();
        event.put(field, value);

        assertThatThrownBy(() -> TransactionEvent.from(event))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining(field);
    }

    @Test
    void oversizedOptionalFields_areMalformed() {
        Map<String, Object> event = valid();
        event.put("deviceId", "d".repeat(101));
        event.put("ipAddress", "1".repeat(46));

        assertThatThrownBy(() -> TransactionEvent.from(event))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("deviceId")
                .hasMessageContaining("ipAddress");
    }

    @Test
    void missingOrUnparseableRequiredFields_areStillMalformed() {
        Map<String, Object> noUser = valid();
        noUser.remove("userId");
        assertThatThrownBy(() -> TransactionEvent.from(noUser)).isInstanceOf(MalformedEventException.class);

        Map<String, Object> badAmount = valid();
        badAmount.put("amount", "not-a-number");
        assertThatThrownBy(() -> TransactionEvent.from(badAmount)).isInstanceOf(MalformedEventException.class);
    }
}
