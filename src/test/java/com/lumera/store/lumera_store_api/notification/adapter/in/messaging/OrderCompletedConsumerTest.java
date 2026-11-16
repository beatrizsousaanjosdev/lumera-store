package com.lumera.store.lumera_store_api.notification.adapter.in.messaging;

import com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand;
import com.lumera.store.lumera_store_api.notification.application.exception.ConflictingNotificationException;
import com.lumera.store.lumera_store_api.notification.application.port.in.NotifyOrderOutcomeUsecase;
import com.lumera.store.lumera_store_api.notification.domain.NotificationOutcome;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCompletedConsumerTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final NotifyOrderOutcomeUsecase notify = mock(NotifyOrderOutcomeUsecase.class);
    final OrderCompletedConsumer consumer = new OrderCompletedConsumer(new IntegrationMessageDecoder(mapper), notify);
    ConsumerRecord<String, String> record(UUID key, String body) {return new ConsumerRecord<>("orders", 0, 0L, key.toString(), body);}

    @ParameterizedTest @EnumSource(NotificationOutcome.class)
    void shouldDelegateBothFinalOutcomes(NotificationOutcome outcome) {
        var message = new OrderCompletedMessage(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), outcome);
        consumer.consume(record(message.orderId(), mapper.writeValueAsString(message)));
        verify(notify).execute(new NotifyOrderOutcomeCommand(message.eventId(), message.orderId(), outcome));
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"{", "{}", "null"})
    void shouldRejectInvalidMessages(String body) {
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(record(UUID.randomUUID(), body)));
        verifyNoInteractions(notify);
    }
    @Test void shouldRejectMismatchedKeyAndNonFinalStatus() {
        UUID order = UUID.randomUUID();
        String body = mapper.writeValueAsString(new OrderCompletedMessage(UUID.randomUUID(), order, Instant.now(), NotificationOutcome.CONFIRMED));
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(record(UUID.randomUUID(), body)));
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(record(order, body.replace("CONFIRMED", "PENDING_PAYMENT"))));
        verifyNoInteractions(notify);
    }
    @Test void shouldSendBusinessConflictToDltButRetryTechnicalFailure() {
        UUID order = UUID.randomUUID();
        String body = mapper.writeValueAsString(new OrderCompletedMessage(UUID.randomUUID(), order, Instant.now(), NotificationOutcome.CONFIRMED));
        when(notify.execute(any())).thenThrow(new ConflictingNotificationException(order));
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(record(order, body)));
        doThrow(new org.springframework.dao.CannotAcquireLockException("Temporary failure")).when(notify).execute(any());
        assertThrows(org.springframework.dao.CannotAcquireLockException.class, () -> consumer.consume(record(order, body)));
    }
}
