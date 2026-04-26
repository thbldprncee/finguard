package com.finguard.transaction.kafka;

import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
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

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "fraud.analysis.completed", groupId = "transaction-status-updater")
    @Transactional
    public void onFraudAnalysisCompleted(Map<String, Object> payload) {
        String transactionIdStr = (String) payload.get("transactionId");
        String decision = (String) payload.get("decision");

        if (transactionIdStr == null || decision == null) {
            log.warn("Received malformed fraud.analysis.completed event: {}", payload);
            return;
        }

        UUID transactionId = UUID.fromString(transactionIdStr);
        Transaction.Status newStatus = switch (decision) {
            case "APPROVED" -> Transaction.Status.APPROVED;
            case "REVIEW_REQUIRED" -> Transaction.Status.REVIEW_REQUIRED;
            case "BLOCKED" -> Transaction.Status.BLOCKED;
            default -> {
                log.warn("Unknown fraud decision: {}", decision);
                yield Transaction.Status.REVIEW_REQUIRED;
            }
        };

        transactionRepository.findById(transactionId).ifPresentOrElse(
                tx -> {
                    tx.setStatus(newStatus);
                    transactionRepository.save(tx);
                    log.info("Updated transaction {} status to {}", transactionId, newStatus);
                },
                () -> log.warn("Transaction not found for status update: {}", transactionId)
        );
    }
}
