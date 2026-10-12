package com.finguard.fraud.service;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.entity.FraudAnalysis;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.rule.RuleResult;
import com.finguard.fraud.rule.TestFraudProperties;
import com.finguard.fraud.rule.TransactionEvent;
import com.finguard.fraud.rule.impl.RepeatedBlockedTransactionsRule;
import com.finguard.fraud.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FraudAnalysisServiceTest {

    @Mock RuleEngineService ruleEngineService;
    @Mock RuleConfigService ruleConfigService;
    @Mock FraudAnalysisRepository analysisRepository;
    @Mock FraudRuleRepository ruleRepository;
    @Mock FraudEvents fraudEvents;
    @Mock RepeatedBlockedTransactionsRule repeatedBlockedRule;

    private final UUID txId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        when(analysisRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private FraudAnalysisService service(FraudProperties properties) {
        return new FraudAnalysisService(ruleEngineService, ruleConfigService, analysisRepository, ruleRepository,
                fraudEvents, repeatedBlockedRule, properties);
    }

    private TransactionEvent event() {
        return new TransactionEvent(txId, userId, new BigDecimal("100"), "USD", "US", "RETAIL", "CARD",
                "device-1", "1.2.3.4", Instant.now());
    }

    private FraudAnalysis savedAnalysis() {
        ArgumentCaptor<FraudAnalysis> captor = ArgumentCaptor.forClass(FraudAnalysis.class);
        verify(analysisRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void duplicateDelivery_isSkipped_withoutSideEffects() {
        when(analysisRepository.existsByTransactionId(txId)).thenReturn(true);

        service(TestFraudProperties.defaults()).analyzeTransaction(event(), "c");

        verifyNoInteractions(ruleEngineService, fraudEvents);
        verify(analysisRepository, never()).saveAndFlush(any());
    }

    @Test
    void lowScore_isApproved_andEventWrittenToOutbox() {
        when(ruleEngineService.evaluate(any())).thenReturn(new RuleEngineService.Evaluation(
                List.of(RuleResult.notTriggered("A")), 0));

        service(TestFraudProperties.defaults()).analyzeTransaction(event(), "corr");

        FraudAnalysis analysis = savedAnalysis();
        assertThat(analysis.getDecision()).isEqualTo(FraudAnalysis.Decision.APPROVED);
        assertThat(analysis.getUserId()).isEqualTo(userId);
        verify(fraudEvents).analysisCompleted(analysis, "corr");
    }

    @Test
    void failedRule_escalatesToReview_insteadOfFailingOpen() {
        when(ruleEngineService.evaluate(any())).thenReturn(new RuleEngineService.Evaluation(
                List.of(RuleResult.failed("VELOCITY", "Rule could not be evaluated")), 0));

        service(TestFraudProperties.defaults()).analyzeTransaction(event(), null);

        FraudAnalysis analysis = savedAnalysis();
        assertThat(analysis.getDecision()).isEqualTo(FraudAnalysis.Decision.REVIEW_REQUIRED);
        assertThat(analysis.getFailedRules()).containsExactly("VELOCITY");
        assertThat(analysis.getExplanation()).contains("VELOCITY");
    }

    @Test
    void failedRule_withBlockPolicy_blocks() {
        when(ruleEngineService.evaluate(any())).thenReturn(new RuleEngineService.Evaluation(
                List.of(RuleResult.failed("VELOCITY", "x")), 0));

        service(TestFraudProperties.with(Map.of("finguard.fraud.decision.on-rule-failure", "BLOCK")))
                .analyzeTransaction(event(), null);

        assertThat(savedAnalysis().getDecision()).isEqualTo(FraudAnalysis.Decision.BLOCKED);
    }

    @Test
    void failure_neverLowersAScoreBasedBlock() {
        when(ruleEngineService.evaluate(any())).thenReturn(new RuleEngineService.Evaluation(
                List.of(RuleResult.triggered("A", "a").withScore(80), RuleResult.failed("B", "x")), 80));

        service(TestFraudProperties.defaults()).analyzeTransaction(event(), null);

        assertThat(savedAnalysis().getDecision()).isEqualTo(FraudAnalysis.Decision.BLOCKED);
    }

    @Test
    void deadLetter_getsFailurePolicyDecision_insteadOfStayingPending() {
        service(TestFraudProperties.defaults()).analyzeAfterFailure(
                Map.of("transactionId", txId.toString(), "userId", userId.toString()), "c", "boom");

        FraudAnalysis analysis = savedAnalysis();
        assertThat(analysis.getDecision()).isEqualTo(FraudAnalysis.Decision.REVIEW_REQUIRED);
        assertThat(analysis.getFailedRules()).containsExactly("ENGINE");
        verify(fraudEvents).analysisCompleted(eq(analysis), eq("c"));
    }

    @Test
    void user_seesOnlySummaryOfOwnAnalysis_andNotOthers() {
        FraudAnalysis analysis = FraudAnalysis.builder().id(UUID.randomUUID()).transactionId(txId).userId(userId)
                .riskScore(55).decision(FraudAnalysis.Decision.REVIEW_REQUIRED)
                .triggeredRules(new String[]{"VELOCITY"}).explanation("secret").build();
        when(analysisRepository.findByTransactionId(txId)).thenReturn(Optional.of(analysis));
        var service = service(TestFraudProperties.defaults());

        var own = service.getByTransactionId(txId, new AuthenticatedUser(userId, "u@e.com", "USER"));
        assertThat(own.decision()).isEqualTo("REVIEW_REQUIRED");
        assertThat(own.triggeredRules()).isNull();
        assertThat(own.explanation()).isNull();
        assertThat(own.riskScore()).isNull();

        assertThatThrownBy(() -> service.getByTransactionId(txId, new AuthenticatedUser(UUID.randomUUID(), "x@e.com", "USER")))
                .isInstanceOf(EntityNotFoundException.class);

        var staff = service.getByTransactionId(txId, new AuthenticatedUser(UUID.randomUUID(), "a@e.com", "ANALYST"));
        assertThat(staff.triggeredRules()).containsExactly("VELOCITY");
    }
}
