package com.finguard.fraud.dto;

import com.finguard.fraud.entity.FraudAnalysis;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record FraudAnalysisResponse(
        UUID id,
        UUID transactionId,
        int riskScore,
        String decision,
        List<String> triggeredRules,
        String explanation,
        Instant analyzedAt
) {
    public static FraudAnalysisResponse from(FraudAnalysis a) {
        return new FraudAnalysisResponse(
                a.getId(), a.getTransactionId(),
                a.getRiskScore(), a.getDecision().name(),
                a.getTriggeredRules() != null ? Arrays.asList(a.getTriggeredRules()) : List.of(),
                a.getExplanation(), a.getAnalyzedAt()
        );
    }
}
