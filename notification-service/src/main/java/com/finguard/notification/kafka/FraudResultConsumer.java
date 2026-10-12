package com.finguard.notification.kafka;

import com.finguard.notification.exception.MalformedEventException;
import com.finguard.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudResultConsumer {

    private final NotificationService notificationService;

    /** Service-specific retry/DLT suffixes: retry topics must not be shared with other consumer groups. */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-notification-retry",
            dltTopicSuffix = "-notification-dlt",
            exclude = MalformedEventException.class,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "fraud.analysis.completed", groupId = "notification-service")
    public void onFraudResult(Map<String, Object> event) {
        log.info("Received fraud result for tx={}", event.get("transactionId"));
        notificationService.processFraudResult(event);
    }

    @DltHandler
    public void onDeadLetter(Map<String, Object> event) {
        log.error("Fraud result could not be turned into a notification: {}", event);
    }
}
