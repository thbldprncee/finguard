package com.finguard.fraud.rule;

import com.finguard.fraud.entity.FraudAnalysis;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.rule.impl.RepeatedBlockedTransactionsRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RepeatedBlockedRuleTest {

    private final FraudAnalysisRepository repository = mock(FraudAnalysisRepository.class);
    private final RepeatedBlockedTransactionsRule rule = new RepeatedBlockedTransactionsRule(repository, TestFraudProperties.defaults());

    @Test
    void countsBlockedDecisionsFromTheDatabase_beforeThisTransactionsCreationTime() {
        UUID user = UUID.randomUUID();
        Instant at = Instant.parse("2026-05-01T12:00:00Z");
        when(repository.countByUserIdAndDecisionAndTransactionOccurredAtGreaterThanEqualAndTransactionOccurredAtLessThan(
                user, FraudAnalysis.Decision.BLOCKED, at.minus(Duration.ofHours(24)), at)).thenReturn(1L);

        RuleResult result = rule.evaluate(RuleContext.builder().transactionId(UUID.randomUUID()).userId(user)
                .amount(BigDecimal.ONE).currency("USD").country("US").occurredAt(at).build());

        assertThat(result.triggered()).isTrue();
    }
}
