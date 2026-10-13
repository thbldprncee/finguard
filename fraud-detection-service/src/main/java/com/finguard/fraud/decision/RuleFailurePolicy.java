package com.finguard.fraud.decision;

import com.finguard.fraud.entity.FraudAnalysis.Decision;

/**
 * Strategy: what to do with a transaction when at least one rule could not be evaluated.
 * Chosen by finguard.fraud.decision.on-rule-failure (see {@link DecisionConfig}).
 * Treating a failed rule as "not triggered" would be fail-open, so every strategy escalates.
 */
public interface RuleFailurePolicy {

    Decision escalate(Decision scoreDecision);

    /** Decision for a transaction whose analysis failed completely (dead-lettered event). */
    Decision forFailedAnalysis();

    /** A human looks at it. */
    final class ReviewOnFailure implements RuleFailurePolicy {
        @Override
        public Decision escalate(Decision scoreDecision) {
            return scoreDecision.atLeast(Decision.REVIEW_REQUIRED);
        }

        @Override
        public Decision forFailedAnalysis() {
            return Decision.REVIEW_REQUIRED;
        }
    }

    /** For flows where an unchecked payment is worse than a false decline. */
    final class BlockOnFailure implements RuleFailurePolicy {
        @Override
        public Decision escalate(Decision scoreDecision) {
            return Decision.BLOCKED;
        }

        @Override
        public Decision forFailedAnalysis() {
            return Decision.BLOCKED;
        }
    }
}
