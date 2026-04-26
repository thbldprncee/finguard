package com.finguard.fraud.kafka;

import com.finguard.fraud.service.FraudAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final FraudAnalysisService fraudAnalysisService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "transaction.created", groupId = "fraud-detection-service")
    public void onTransactionCreated(
            Map<String, Object> event,
            @Header(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Received transaction.created event: txId={}", event.get("transactionId"));
        fraudAnalysisService.analyzeTransaction(event, correlationId);
    }
}
