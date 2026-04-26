package com.finguard.transaction.kafka;

import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.kafka.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {

    private static final String TOPIC_CREATED = "transaction.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTransactionCreated(Transaction tx, String correlationId) {
        var event = new TransactionCreatedEvent(
                tx.getId(), tx.getExternalTransactionId(), tx.getUserId(),
                tx.getSourceAccount(), tx.getTargetAccount(), tx.getAmount(),
                tx.getCurrency(), tx.getCountry(), tx.getMerchantCategory(),
                tx.getPaymentMethod(), tx.getIpAddress(), tx.getDeviceId(),
                correlationId, Instant.now()
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_CREATED, tx.getId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish transaction.created event for tx={}: {}", tx.getId(), ex.getMessage());
            } else {
                log.info("Published transaction.created: txId={}, offset={}",
                        tx.getId(), result.getRecordMetadata().offset());
            }
        });
    }
}
