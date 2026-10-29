package com.finguard.fraud.kafka;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @Test
    void wellFormedHeader_isKept() {
        assertThat(TransactionEventConsumer.correlationIdOf(Map.of(), "corr-pipeline")).isEqualTo("corr-pipeline");
    }

    @Test
    void payloadCopy_isTheFallback() {
        assertThat(TransactionEventConsumer.correlationIdOf(Map.of("correlationId", "abc-12345678"), null))
                .isEqualTo("abc-12345678");
    }

    @Test
    void oversizedOrForeignValues_areDropped_insteadOfBreakingTheAnalysis() {
        assertThat(TransactionEventConsumer.correlationIdOf(Map.of(), "x".repeat(65))).isNull();
        assertThat(TransactionEventConsumer.correlationIdOf(Map.of("correlationId", "<script>"), "bad id")).isNull();
    }
}
