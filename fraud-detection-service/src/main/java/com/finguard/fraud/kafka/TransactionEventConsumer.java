package com.finguard.fraud.kafka;

import com.finguard.fraud.exception.MalformedEventException;
import com.finguard.fraud.rule.TransactionEvent;
import com.finguard.fraud.service.FraudAnalysisService;
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
            retryTopicSuffix = "-fraud-retry",
            dltTopicSuffix = "-fraud-dlt",
            exclude = MalformedEventException.class,
            // A failing DLT handler must not re-publish to the DLT forever (the default strategy).
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "transaction.created", groupId = "fraud-detection-service")
    public void onTransactionCreated(
            Map<String, Object> event,
            @Header(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Received transaction.created event: txId={}", event.get("transactionId"));
        fraudAnalysisService.analyzeTransaction(TransactionEvent.from(event), correlationIdOf(event, correlationId));
    }

    @DltHandler
    public void onDeadLetter(
            Map<String, Object> event,
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        log.error("transaction.created moved to DLT: txId={}, error={}", event.get("transactionId"), error);
        fraudAnalysisService.analyzeAfterFailure(event, correlationIdOf(event, correlationId),
                error != null ? error : "unknown error");
    }

    /** The outbox relay sets the header; the payload copy is a fallback for older producers. */
    private static String correlationIdOf(Map<String, Object> event, String header) {
        if (header != null && !header.isBlank()) {
            return header;
        }
        Object fromPayload = event.get("correlationId");
        return fromPayload != null ? fromPayload.toString() : null;
    }
}
