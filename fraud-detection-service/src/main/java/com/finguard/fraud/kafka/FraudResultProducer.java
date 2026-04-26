package com.finguard.fraud.kafka;

import com.finguard.fraud.entity.FraudAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudResultProducer {

    private static final String TOPIC = "fraud.analysis.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishFraudResult(UUID transactionId, UUID userId, int riskScore,
                                    FraudAnalysis.Decision decision, String[] triggeredRules,
                                    String explanation, String correlationId) {
        Map<String, Object> event = new HashMap<>();
        event.put("transactionId", transactionId.toString());
        event.put("userId", userId.toString());
        event.put("riskScore", riskScore);
        event.put("decision", decision.name());
        event.put("triggeredRules", triggeredRules != null ? Arrays.asList(triggeredRules) : java.util.List.of());
        event.put("explanation", explanation);
        event.put("correlationId", correlationId != null ? correlationId : "");
        event.put("occurredAt", Instant.now().toString());

        kafkaTemplate.send(TOPIC, transactionId.toString(), event);
        log.info("Published fraud result: txId={}, decision={}, score={}", transactionId, decision, riskScore);
    }
}
