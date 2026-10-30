package com.finguard.fraud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finguard.fraud.entity.FraudAnalysis;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Full form for ANALYST/ADMIN. A USER gets {@link #summaryOf}: which rules fired and how they
 * are scored would teach a fraudster exactly what to change to get through.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FraudAnalysisResponse(
        UUID id,
        UUID transactionId,
        Integer riskScore,
        String decision,
        List<String> triggeredRules,
        List<String> failedRules,
        String explanation,
        Instant analyzedAt,
        /** Staff only; shadow-mode ML fraud probability, null without a model. */
        Double mlScore,
        String mlModelVersion,
        /** Staff only; an analyst's verdict (true = fraud), null while unlabelled. */
        Boolean labeledFraud
) {
    public static FraudAnalysisResponse from(FraudAnalysis a) {
        return from(a, null);
    }

    public static FraudAnalysisResponse from(FraudAnalysis a, Boolean labeledFraud) {
        return new FraudAnalysisResponse(
                a.getId(), a.getTransactionId(),
                a.getRiskScore(), a.getDecision().name(),
                list(a.getTriggeredRules()), list(a.getFailedRules()),
                a.getExplanation(), a.getAnalyzedAt(),
                a.getMlScore(), a.getMlModelVersion(), labeledFraud
        );
    }

    public static FraudAnalysisResponse summaryOf(FraudAnalysis a) {
        return new FraudAnalysisResponse(null, a.getTransactionId(), null, a.getDecision().name(),
                null, null, null, a.getAnalyzedAt(), null, null, null);
    }

    private static List<String> list(String[] values) {
        return values != null ? Arrays.asList(values) : List.of();
    }
}
