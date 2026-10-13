package com.finguard.transaction.kafka;

import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.exception.MalformedEventException;
import com.finguard.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionStatusConsumer {

    private final TransactionRepository transactionRepository;

    /**
     * Retry/DLT topics carry a service-specific suffix. With the default suffix every consumer
     * group of fraud.analysis.completed would share "-retry-0", and a message failing in one
     * service would be re-delivered to the others.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-txn-retry",
            dltTopicSuffix = "-txn-dlt",
            exclude = MalformedEventException.class,
            // A failing DLT handler must not re-publish to the DLT forever (the default strategy).
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "fraud.analysis.completed", groupId = "transaction-status-updater")
    @Transactional
    public void onFraudAnalysisCompleted(Map<String, Object> payload) {
        UUID transactionId = parseId(payload.get("transactionId"));
        Transaction.Status newStatus = switch (String.valueOf(payload.get("decision"))) {
            case "APPROVED" -> Transaction.Status.APPROVED;
            case "REVIEW_REQUIRED" -> Transaction.Status.REVIEW_REQUIRED;
            case "BLOCKED" -> Transaction.Status.BLOCKED;
            default -> throw new MalformedEventException("Unknown fraud decision: " + payload.get("decision"));
        };

        // The transaction's current state decides whether the decision applies (State pattern);
        // @Version turns a concurrent update into a retry instead of a lost write.
        transactionRepository.findById(transactionId).ifPresentOrElse(
                tx -> {
                    if (tx.applyFraudDecision(newStatus)) {
                        log.info("Updated transaction {} status to {}", transactionId, newStatus);
                    } else {
                        log.info("Ignored decision {} for transaction {} in state {}", newStatus, transactionId, tx.getStatus());
                    }
                },
                () -> log.warn("Transaction {} not found for decision {}", transactionId, newStatus));
    }

    @DltHandler
    public void onDeadLetter(Map<String, Object> payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Fraud decision could not be applied and was moved to {}: {}", topic, payload);
    }

    private static UUID parseId(Object value) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Invalid transactionId: " + value);
        }
    }
}
