package com.finguard.fraud.decision;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.entity.FraudAnalysis.Decision;
import com.finguard.fraud.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Score thresholds, then the failure strategy if any rule could not be evaluated. */
@Component
@RequiredArgsConstructor
public class DecisionMaker {

    private final FraudProperties properties;
    private final RuleFailurePolicy failurePolicy;

    public Decision decide(RuleEngineService.Evaluation evaluation) {
        Decision byScore = byScore(evaluation.riskScore());
        return evaluation.failed().isEmpty() ? byScore : failurePolicy.escalate(byScore);
    }

    public Decision forFailedAnalysis() {
        return failurePolicy.forFailedAnalysis();
    }

    private Decision byScore(int score) {
        if (score >= properties.decision().blockThreshold()) return Decision.BLOCKED;
        if (score >= properties.decision().reviewThreshold()) return Decision.REVIEW_REQUIRED;
        return Decision.APPROVED;
    }
}
