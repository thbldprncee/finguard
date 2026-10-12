package com.finguard.notification.service;

import com.finguard.notification.entity.Notification;
import com.finguard.notification.exception.MalformedEventException;
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

    /** Idempotent: a re-delivered event does not notify the user twice (also enforced by a unique index). */
    @Transactional
    public void processFraudResult(Map<String, Object> event) {
        String decision = String.valueOf(event.get("decision"));
        if ("APPROVED".equals(decision)) {
            return;
        }
        String type = switch (decision) {
            case "REVIEW_REQUIRED" -> "FRAUD_REVIEW";
            case "BLOCKED" -> "FRAUD_BLOCKED";
            default -> throw new MalformedEventException("Unknown decision: " + decision);
        };
        UUID transactionId = uuid(event, "transactionId");
        UUID userId = uuid(event, "userId");

        if (notificationRepository.existsByTransactionIdAndType(transactionId, type)) {
            log.info("Notification {} for tx={} already sent, skipping duplicate", type, transactionId);
            return;
        }

        String message = "BLOCKED".equals(decision)
                ? String.format("Transaction %s has been blocked for your protection. Contact support if this was a legitimate transaction.", transactionId)
                : String.format("Transaction %s is being reviewed by our team. We will notify you of the outcome.", transactionId);

        notificationRepository.save(Notification.builder()
                .userId(userId)
                .transactionId(transactionId)
                .type(type)
                .message(message)
                .status("SENT")
                .build());

        log.info("[NOTIFY] {} to user {} for tx {}", type, userId, transactionId);
    }

    private static UUID uuid(Map<String, Object> event, String field) {
        try {
            return UUID.fromString(String.valueOf(event.get(field)));
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Invalid " + field + ": " + event.get(field));
        }
    }
}
