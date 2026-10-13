package com.finguard.transaction.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table and publishes events to Kafka.
 * Delivery is at-least-once: if the service dies after Kafka acknowledged a record but before
 * the row is marked published, the record is sent again. Consumers deduplicate by X-Event-Id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    public static final String HEADER_EVENT_ID = "X-Event-Id";
    public static final String HEADER_EVENT_TYPE = "X-Event-Type";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;

    /** Each batch holds at most one event per key, so keep going while batches make progress. */
    public void publishPending() {
        Integer published;
        do {
            published = transactionTemplate.execute(status -> publishBatch());
        } while (published != null && published > 0);
    }

    @Transactional
    public void cleanupPublished() {
        int deleted = repository.deletePublishedBefore(Instant.now().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, properties.retention());
        }
    }

    private int publishBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(Instant.now(), properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(toRecord(event))
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.setPublishedAt(Instant.now());
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markFailed(event, e);
                break;
            } catch (Exception e) {
                // Only this key waits: its later events are not selected until this one is published.
                markFailed(event, e);
            }
        }
        return published;
    }

    private ProducerRecord<String, Object> toRecord(OutboxEvent event) {
        var record = new ProducerRecord<String, Object>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers().add(HEADER_EVENT_ID, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        if (event.getCorrelationId() != null) {
            record.headers().add(HEADER_CORRELATION_ID, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private void markFailed(OutboxEvent event, Exception e) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setNextAttemptAt(Instant.now().plus(backoff(attempts)));
        String message = String.valueOf(e.getMessage());
        event.setLastError(message.length() > 1000 ? message.substring(0, 1000) : message);
        log.warn("Failed to publish outbox event id={} type={} attempt={}: {}",
                event.getId(), event.getEventType(), attempts, message);
    }

    private Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts - 1, 20);
        Duration delay = properties.initialBackoff().multipliedBy(factor);
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }
}
