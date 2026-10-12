package com.finguard.notification.service;

import com.finguard.notification.exception.MalformedEventException;
import com.finguard.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repository;
    @InjectMocks NotificationService service;

    private Map<String, Object> event(String decision) {
        return Map.of("transactionId", UUID.randomUUID().toString(), "userId", UUID.randomUUID().toString(),
                "decision", decision, "riskScore", 80);
    }

    @Test
    void blocked_createsNotification() {
        service.processFraudResult(event("BLOCKED"));
        verify(repository).save(argThat(n -> n.getType().equals("FRAUD_BLOCKED")));
    }

    @Test
    void approved_createsNothing() {
        service.processFraudResult(event("APPROVED"));
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateDelivery_isSkipped() {
        when(repository.existsByTransactionIdAndType(any(), any())).thenReturn(true);
        service.processFraudResult(event("REVIEW_REQUIRED"));
        verify(repository, never()).save(any());
    }

    @Test
    void missingUserId_isMalformed_notNullPointer() {
        assertThatThrownBy(() -> service.processFraudResult(Map.of("transactionId", UUID.randomUUID().toString(), "decision", "BLOCKED")))
                .isInstanceOf(MalformedEventException.class);
    }
}
