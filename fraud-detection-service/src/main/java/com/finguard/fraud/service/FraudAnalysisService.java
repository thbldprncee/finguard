package com.finguard.fraud.service;

import com.finguard.fraud.dto.FraudAnalysisResponse;
import com.finguard.fraud.entity.FraudAnalysis;
import com.finguard.fraud.kafka.FraudResultProducer;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAnalysisService {

    private final RuleEngineService ruleEngineService;
    private final FraudAnalysisRepository fraudAnalysisRepository;
    private final FraudResultProducer fraudResultProducer;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void analyzeTransaction(Map<String, Object> event, String correlationId) {
        UUID transactionId = UUID.fromString((String) event.get("transactionId"));
        UUID userId = UUID.fromString((String) event.get("userId"));

        RuleContext context = RuleContext.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(new BigDecimal(event.get("amount").toString()))
                .currency((String) event.get("currency"))
                .country((String) event.get("country"))
                .merchantCategory((String) event.get("merchantCategory"))
                .paymentMethod((String) event.get("paymentMethod"))
                .deviceId((String) event.get("deviceId"))
                .ipAddress((String) event.get("ipAddress"))
                .occurredAt(Instant.now())
                .build();

        List<RuleResult> results = ruleEngineService.evaluate(context);

        int totalScore = results.stream()
                .filter(RuleResult::triggered)
                .mapToInt(RuleResult::scoreContribution)
                .sum();
        int riskScore = Math.min(100, totalScore);

        FraudAnalysis.Decision decision = determineDecision(riskScore);

        String[] triggeredRules = results.stream()
                .filter(RuleResult::triggered)
                .map(RuleResult::ruleCode)
                .toArray(String[]::new);

        String explanation = results.stream()
                .filter(RuleResult::triggered)
                .map(RuleResult::reason)
                .reduce((a, b) -> a + "; " + b)
                .orElse("No suspicious patterns detected");

        FraudAnalysis analysis = FraudAnalysis.builder()
                .transactionId(transactionId)
                .riskScore(riskScore)
                .decision(decision)
                .triggeredRules(triggeredRules)
                .explanation(explanation)
                .build();

        fraudAnalysisRepository.save(analysis);

        if (decision == FraudAnalysis.Decision.BLOCKED) {
            String key = "blocked_count:" + userId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
        }

        log.info("Fraud analysis complete: txId={}, score={}, decision={}", transactionId, riskScore, decision);

        fraudResultProducer.publishFraudResult(transactionId, userId, riskScore, decision, triggeredRules, explanation, correlationId);
    }

    @Transactional(readOnly = true)
    public FraudAnalysisResponse getByTransactionId(UUID transactionId) {
        return fraudAnalysisRepository.findByTransactionId(transactionId)
                .map(FraudAnalysisResponse::from)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Fraud analysis not found for transaction: " + transactionId));
    }

    @Transactional(readOnly = true)
    public Page<FraudAnalysisResponse> getAlerts(Pageable pageable) {
        return fraudAnalysisRepository.findAlerts(pageable).map(FraudAnalysisResponse::from);
    }

    private FraudAnalysis.Decision determineDecision(int score) {
        if (score >= 70) return FraudAnalysis.Decision.BLOCKED;
        if (score >= 40) return FraudAnalysis.Decision.REVIEW_REQUIRED;
        return FraudAnalysis.Decision.APPROVED;
    }
}
