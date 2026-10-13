package com.finguard.transaction.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStatusTest {

    private Transaction tx(Transaction.Status status) {
        return Transaction.builder().status(status).build();
    }

    @ParameterizedTest
    @EnumSource(value = Transaction.Status.class, names = {"APPROVED", "REVIEW_REQUIRED", "BLOCKED"})
    void pending_acceptsAnyDecision(Transaction.Status decision) {
        Transaction tx = tx(Transaction.Status.PENDING);
        assertThat(tx.applyFraudDecision(decision)).isTrue();
        assertThat(tx.getStatus()).isEqualTo(decision);
    }

    @Test
    void finalStates_ignoreLaterDecisions() {
        Transaction blocked = tx(Transaction.Status.BLOCKED);
        assertThat(blocked.applyFraudDecision(Transaction.Status.APPROVED)).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(Transaction.Status.BLOCKED);
    }

    @Test
    void reviewRequired_isNotOverriddenByAReplayedAutomatedDecision() {
        Transaction review = tx(Transaction.Status.REVIEW_REQUIRED);
        assertThat(review.applyFraudDecision(Transaction.Status.APPROVED)).isFalse();
        assertThat(review.getStatus()).isEqualTo(Transaction.Status.REVIEW_REQUIRED);
    }

    @Test
    void pendingIsNotADecision() {
        assertThatThrownBy(() -> tx(Transaction.Status.PENDING).applyFraudDecision(Transaction.Status.PENDING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
