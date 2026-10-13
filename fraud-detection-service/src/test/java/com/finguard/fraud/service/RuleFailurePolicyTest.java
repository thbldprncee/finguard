package com.finguard.fraud.service;

import com.finguard.fraud.decision.RuleFailurePolicy;
import com.finguard.fraud.entity.FraudAnalysis.Decision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFailurePolicyTest {

    @Test
    void reviewStrategy_neverLowersAndAtLeastReviews() {
        var review = new RuleFailurePolicy.ReviewOnFailure();
        assertThat(review.escalate(Decision.APPROVED)).isEqualTo(Decision.REVIEW_REQUIRED);
        assertThat(review.escalate(Decision.BLOCKED)).isEqualTo(Decision.BLOCKED);
    }

    @Test
    void blockStrategy_alwaysBlocks() {
        var block = new RuleFailurePolicy.BlockOnFailure();
        assertThat(block.escalate(Decision.APPROVED)).isEqualTo(Decision.BLOCKED);
        assertThat(block.forFailedAnalysis()).isEqualTo(Decision.BLOCKED);
    }
}
