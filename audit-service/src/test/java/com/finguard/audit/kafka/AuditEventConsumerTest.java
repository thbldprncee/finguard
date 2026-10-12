package com.finguard.audit.kafka;

import com.finguard.audit.entity.AuditEvent;
import com.finguard.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock AuditEventRepository repository;
    @InjectMocks AuditEventConsumer consumer;

    @Test
    void storesEventWithHeadersFromOutbox() {
        UUID eventId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        consumer.onEvent(Map.of("transactionId", txId.toString()), "transaction.created",
                eventId.toString(), "TRANSACTION_CREATED", "corr-1");

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(eventId);
        assertThat(saved.getValue().getEventType()).isEqualTo("TRANSACTION_CREATED");
        assertThat(saved.getValue().getAggregateId()).isEqualTo(txId.toString());
        assertThat(saved.getValue().getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void redeliveredEvent_isStoredOnce() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        consumer.onEvent(Map.of(), "audit.event.created", eventId.toString(), "USER_LOGIN", null);

        verify(repository, never()).save(any());
    }

    @Test
    void eventWithoutType_fallsBackToTopicName_notUnknown() {
        consumer.onEvent(Map.of("transactionId", "t-1"), "transaction.created", null, null, null);

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("transaction.created");
    }
}
