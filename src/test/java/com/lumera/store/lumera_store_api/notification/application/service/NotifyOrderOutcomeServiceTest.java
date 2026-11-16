package com.lumera.store.lumera_store_api.notification.application.service;

import com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand;
import com.lumera.store.lumera_store_api.notification.application.exception.ConflictingNotificationException;
import com.lumera.store.lumera_store_api.notification.application.port.out.*;
import com.lumera.store.lumera_store_api.notification.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotifyOrderOutcomeServiceTest {
    final NotificationRepositoryPort repository = mock(NotificationRepositoryPort.class);
    final NotificationDeliveryPort delivery = mock(NotificationDeliveryPort.class);
    final NotifyOrderOutcomeService service = new NotifyOrderOutcomeService(repository, delivery);

    @ParameterizedTest @EnumSource(NotificationOutcome.class)
    void shouldLockPersistAndDeliverBothOutcomes(NotificationOutcome outcome) {
        var command = new NotifyOrderOutcomeCommand(UUID.randomUUID(), UUID.randomUUID(), outcome);
        var notification = service.execute(command);
        assertEquals(command.eventId(), notification.eventId());
        assertEquals(command.orderId(), notification.orderId());
        assertEquals(outcome, notification.outcome());
        assertTrue(notification.message().contains(command.orderId().toString()));
        var sequence = inOrder(repository, delivery);
        sequence.verify(repository).lockByOrderId(command.orderId());
        sequence.verify(repository).findByOrderId(command.orderId());
        sequence.verify(repository).save(notification);
        sequence.verify(delivery).deliver(notification);
    }

    @ParameterizedTest @EnumSource(NotificationOutcome.class)
    void shouldIgnoreRepeatedOrDifferentEventForSameOutcome(NotificationOutcome outcome) {
        UUID order = UUID.randomUUID();
        var existing = new OrderNotification(UUID.randomUUID(), UUID.randomUUID(), order, outcome, Instant.now());
        when(repository.findByOrderId(order)).thenReturn(Optional.of(existing));
        assertSame(existing, service.execute(new NotifyOrderOutcomeCommand(existing.eventId(), order, outcome)));
        assertSame(existing, service.execute(new NotifyOrderOutcomeCommand(UUID.randomUUID(), order, outcome)));
        verify(repository, never()).save(any());
        verifyNoInteractions(delivery);
    }

    @Test void shouldRejectContradictoryOutcome() {
        UUID order = UUID.randomUUID();
        when(repository.findByOrderId(order)).thenReturn(Optional.of(new OrderNotification(UUID.randomUUID(), UUID.randomUUID(), order, NotificationOutcome.CONFIRMED, Instant.now())));
        assertThrows(ConflictingNotificationException.class, () -> service.execute(new NotifyOrderOutcomeCommand(UUID.randomUUID(), order, NotificationOutcome.CANCELLED)));
        verify(repository, never()).save(any());verifyNoInteractions(delivery);
    }

    @Test void shouldNotDeliverWhenPersistenceFails() {
        doThrow(new IllegalStateException("Database unavailable")).when(repository).save(any());
        assertThrows(IllegalStateException.class, () -> service.execute(new NotifyOrderOutcomeCommand(UUID.randomUUID(), UUID.randomUUID(), NotificationOutcome.CONFIRMED)));
        verifyNoInteractions(delivery);
    }

    @Test void shouldPropagateDeliveryFailuresForRetry() {
        doThrow(new IllegalStateException("Delivery unavailable")).when(delivery).deliver(any());
        assertThrows(IllegalStateException.class, () -> service.execute(new NotifyOrderOutcomeCommand(UUID.randomUUID(), UUID.randomUUID(), NotificationOutcome.CONFIRMED)));
    }
}
