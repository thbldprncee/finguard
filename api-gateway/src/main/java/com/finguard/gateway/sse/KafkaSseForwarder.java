package com.finguard.gateway.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaSseForwarder {

    private final SseController sseController;

    /**
     * One consumer group per gateway instance: with a shared group each instance would get
     * only some partitions, and clients connected to it would miss the other events.
     */
    @KafkaListener(
            topics = {"transaction.created", "fraud.analysis.completed"},
            groupId = "#{'api-gateway-sse-' + T(java.util.UUID).randomUUID()}")
    public void onKafkaEvent(Map<String, Object> event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.debug("Forwarding {} to SSE", topic);
        sseController.publishEvent(topic, event);
    }
}
