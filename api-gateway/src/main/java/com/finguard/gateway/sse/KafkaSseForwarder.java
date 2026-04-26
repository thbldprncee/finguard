package com.finguard.gateway.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaSseForwarder {

    private final SseController sseController;

    @KafkaListener(topics = {
            "transaction.created",
            "fraud.analysis.completed"
    }, groupId = "api-gateway-sse")
    public void onKafkaEvent(Map<String, Object> event) {
        log.debug("Forwarding Kafka event to SSE: {}", event.get("eventType"));
        sseController.publishEvent(event);
    }
}
