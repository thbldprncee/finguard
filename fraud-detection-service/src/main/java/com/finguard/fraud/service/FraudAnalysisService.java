package com.finguard.fraud.service;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.dto.FraudAnalysisResponse;
import com.finguard.fraud.dto.FraudRuleUpdateRequest;
import com.finguard.fraud.entity.FraudAnalysis;
import com.finguard.fraud.entity.FraudRule;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.rule.RuleResult;
import com.finguard.fraud.rule.TransactionEvent;
import com.finguard.fraud.rule.impl.RepeatedBlockedTransactionsRule;
import com.finguard.fraud.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAnalysisService {

    private final RuleEngineService ruleEngineService;
    private final RuleConfigService ruleConfigService;
    private final FraudAnalysisRepository fraudAnalysisRepository;
    private final FraudRuleRepository fraudRuleRepository;
    private final FraudEvents fraudEvents;
    private final RepeatedBlockedTransactionsRule repeatedBlockedRule;
    private final FraudProperties properties;

    /**
     * Idempotent consumer: Kafka delivers at least once, so the same transaction may arrive again.
     * An existing analysis means the decision and its outbox event are already committed.
     * The rules themselves are idempotent per transaction id as well (see the Redis-based rules),
     * so a retry after a failed commit produces the same result.
     */
    @Transactional
    public void analyzeTransaction(TransactionEvent event, String correlationId) {
        if (fraudAnalysisRepository.existsByTransactionId(event.transactionId())) {
            log.info("Transaction {} already analysed, skipping duplicate delivery", event.transactionId());
            return;
        }

        RuleEngineService.Evaluation evaluation = ruleEngineService.evaluate(event.toRuleContext());
        List<RuleResult> failed = evaluation.failed();

        FraudAnalysis.Decision decision = decisionForScore(evaluation.riskScore());
        if (!failed.isEmpty()) {
            decision = decision.atLeast(failurePolicyDecision());
        }

        List<String> reasons = new ArrayList<>(evaluation.triggered().stream().map(RuleResult::reason).toList());
        if (!failed.isEmpty()) {
            reasons.add("Not evaluated: " + String.join(", ", failed.stream().map(RuleResult::ruleCode).toList())
                    + " - decision escalated to " + decision);
        }

        FraudAnalysis analysis = fraudAnalysisRepository.saveAndFlush(FraudAnalysis.builder()
                .transactionId(event.transactionId())
                .userId(event.userId())
                .riskScore(evaluation.riskScore())
                .decision(decision)
                .triggeredRules(evaluation.triggered().stream().map(RuleResult::ruleCode).toArray(String[]::new))
                .failedRules(failed.stream().map(RuleResult::ruleCode).toArray(String[]::new))
                .explanation(reasons.isEmpty() ? "No suspicious patterns detected" : String.join("; ", reasons))
                .build());
        fraudEvents.analysisCompleted(analysis, correlationId);

        if (decision == FraudAnalysis.Decision.BLOCKED) {
            afterCommit(() -> repeatedBlockedRule.recordBlocked(event.userId(), event.transactionId(), event.occurredAt()));
        }
        log.info("Fraud analysis complete: txId={}, score={}, decision={}, failedRules={}",
                event.transactionId(), evaluation.riskScore(), decision, failed.size());
    }

    /**
     * Last resort for an event that exhausted its retries. Leaving the transaction PENDING
     * forever would be a silent fail-open, so it gets the failure-policy decision instead.
     */
    @Transactional
    public void analyzeAfterFailure(Map<String, Object> rawEvent, String correlationId, String error) {
        UUID transactionId;
        UUID userId;
        try {
            transactionId = UUID.fromString(String.valueOf(rawEvent.get("transactionId")));
            userId = UUID.fromString(String.valueOf(rawEvent.get("userId")));
        } catch (IllegalArgumentException e) {
            log.error("Dead-lettered event without usable ids, cannot decide: {}", rawEvent);
            return;
        }
        if (fraudAnalysisRepository.existsByTransactionId(transactionId)) {
            return;
        }

        FraudAnalysis.Decision decision = failurePolicyDecision();
        FraudAnalysis analysis = fraudAnalysisRepository.saveAndFlush(FraudAnalysis.builder()
                .transactionId(transactionId)
                .userId(userId)
                .riskScore(0)
                .decision(decision)
                .triggeredRules(new String[0])
                .failedRules(new String[]{"ENGINE"})
                .explanation("Automatic analysis failed (" + error + ") - decision set to " + decision)
                .build());
        fraudEvents.analysisCompleted(analysis, correlationId);
        log.warn("Transaction {} could not be analysed, decision {} by failure policy", transactionId, decision);
    }

    /** Staff see everything; a USER only sees a summary of their own transactions (404 otherwise). */
    @Transactional(readOnly = true)
    public FraudAnalysisResponse getByTransactionId(UUID transactionId, AuthenticatedUser user) {
        FraudAnalysis analysis = fraudAnalysisRepository.findByTransactionId(transactionId)
                .filter(a -> user.isStaff() || user.userId().equals(a.getUserId()))
                .orElseThrow(() -> new EntityNotFoundException("Fraud analysis not found for transaction: " + transactionId));
        return user.isStaff() ? FraudAnalysisResponse.from(analysis) : FraudAnalysisResponse.summaryOf(analysis);
    }

    @Transactional(readOnly = true)
    public Page<FraudAnalysisResponse> getAlerts(Pageable pageable) {
        return fraudAnalysisRepository.findAlerts(pageable).map(FraudAnalysisResponse::from);
    }

    @Transactional
    public FraudRule updateRule(UUID ruleId, FraudRuleUpdateRequest request, UUID actorId, String correlationId) {
        FraudRule rule = fraudRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Fraud rule not found: " + ruleId));
        boolean oldEnabled = rule.isEnabled();
        int oldWeight = rule.getWeight();

        if (request.enabled() != null) rule.setEnabled(request.enabled());
        if (request.weight() != null) rule.setWeight(request.weight());

        FraudRule saved = fraudRuleRepository.save(rule);
        fraudEvents.ruleUpdated(saved, oldEnabled, oldWeight, actorId, correlationId);
        afterCommit(ruleConfigService::reload);
        log.info("Fraud rule {} updated by {}: enabled {} -> {}, weight {} -> {}",
                rule.getCode(), actorId, oldEnabled, rule.isEnabled(), oldWeight, rule.getWeight());
        return saved;
    }

    private FraudAnalysis.Decision decisionForScore(int score) {
        if (score >= properties.decision().blockThreshold()) return FraudAnalysis.Decision.BLOCKED;
        if (score >= properties.decision().reviewThreshold()) return FraudAnalysis.Decision.REVIEW_REQUIRED;
        return FraudAnalysis.Decision.APPROVED;
    }

    private FraudAnalysis.Decision failurePolicyDecision() {
        return properties.decision().onRuleFailure() == FraudProperties.FailurePolicy.BLOCK
                ? FraudAnalysis.Decision.BLOCKED
                : FraudAnalysis.Decision.REVIEW_REQUIRED;
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.error("After-commit action failed: {}", e.getMessage(), e);
                }
            }
        });
    }
}
