package com.finguard.notification.service;

import com.finguard.notification.entity.Notification;
import com.finguard.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void processfraudResult(Map<String, Object> event) {
        String transactionId = (String) event.get("transactionId");
        String userId = (String) event.get("userId");
        String decision = (String) event.get("decision");
        int riskScore = ((Number) event.get("riskScore")).intValue();

        if ("APPROVED".equals(decision)) {
            return;
        }

        String type = "REVIEW_REQUIRED".equals(decision) ? "FRAUD_REVIEW" : "FRAUD_BLOCKED";
        String message = buildMessage(transactionId, decision, riskScore);

        Notification notification = Notification.builder()
                .userId(UUID.fromString(userId))
                .transactionId(UUID.fromString(transactionId))
                .type(type)
                .message(message)
                .status("SENT")
                .build();

        notificationRepository.save(notification);

        log.info("[NOTIFY] {} to user {}: {}", type, userId, message);
    }

    private String buildMessage(String txId, String decision, int score) {
        return switch (decision) {
            case "REVIEW_REQUIRED" ->
                String.format("Transaction %s requires manual review. Risk score: %d. Our team will investigate.", txId, score);
            case "BLOCKED" ->
                String.format("Transaction %s has been BLOCKED due to high fraud risk score: %d. Contact support if this was a legitimate transaction.", txId, score);
            default -> String.format("Transaction %s status: %s", txId, decision);
        };
    }
}
