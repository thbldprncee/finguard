package com.finguard.fraud.service;

import com.finguard.fraud.entity.FraudAnalysis;
import com.finguard.fraud.entity.FraudRule;
import com.finguard.fraud.outbox.OutboxMessage;
import com.finguard.fraud.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FraudEvents {

    static final String TOPIC_ANALYSIS_COMPLETED = "fraud.analysis.completed";
    static final String TOPIC_AUDIT = "audit.event.created";

    private final OutboxWriter outboxWriter;

    /** Written in the same transaction as the analysis row: the decision is never lost or orphaned. */
    public void analysisCompleted(FraudAnalysis analysis, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", analysis.getTransactionId().toString());
        payload.put("aggregateId", analysis.getTransactionId().toString());
        payload.put("userId", analysis.getUserId() != null ? analysis.getUserId().toString() : null);
        payload.put("riskScore", analysis.getRiskScore());
        payload.put("decision", analysis.getDecision().name());
        payload.put("triggeredRules", list(analysis.getTriggeredRules()));
        payload.put("failedRules", list(analysis.getFailedRules()));
        payload.put("explanation", analysis.getExplanation());

        outboxWriter.enqueue(new OutboxMessage(
                TOPIC_ANALYSIS_COMPLETED, "TRANSACTION", analysis.getTransactionId().toString(),
                analysis.getTransactionId().toString(), "FRAUD_ANALYSIS_COMPLETED", payload, correlationId));
    }

    public void ruleUpdated(FraudRule rule, boolean oldEnabled, int oldWeight, UUID actorId, String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", rule.getCode());
        data.put("oldEnabled", oldEnabled);
        data.put("newEnabled", rule.isEnabled());
        data.put("oldWeight", oldWeight);
        data.put("newWeight", rule.getWeight());
        data.put("actorId", actorId.toString());

        outboxWriter.enqueue(new OutboxMessage(
                TOPIC_AUDIT, "FRAUD_RULE", rule.getId().toString(), rule.getId().toString(), "FRAUD_RULE_UPDATED",
                Map.of("aggregateId", rule.getId().toString(), "aggregateType", "FRAUD_RULE", "data", data),
                correlationId));
    }

    private static List<String> list(String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }
}
