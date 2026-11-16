package com.lumera.store.lumera_store_api.notification.application.service;

import com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand;
import com.lumera.store.lumera_store_api.notification.application.exception.ConflictingNotificationException;
import com.lumera.store.lumera_store_api.notification.application.port.in.NotifyOrderOutcomeUsecase;
import com.lumera.store.lumera_store_api.notification.application.port.out.NotificationDeliveryPort;
import com.lumera.store.lumera_store_api.notification.application.port.out.NotificationRepositoryPort;
import com.lumera.store.lumera_store_api.notification.domain.OrderNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotifyOrderOutcomeService implements NotifyOrderOutcomeUsecase {
    private final NotificationRepositoryPort repository;
    private final NotificationDeliveryPort delivery;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderNotification execute(NotifyOrderOutcomeCommand command) {
        repository.lockByOrderId(command.orderId());
        var existing = repository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            var notification = existing.get();
            if (notification.outcome() != command.outcome()) {
                throw new ConflictingNotificationException(command.orderId());
            }
            return notification;
        }

        var notification = new OrderNotification(UUID.randomUUID(), command.eventId(), command.orderId(),
                command.outcome(), Instant.now());
        repository.save(notification);
        delivery.deliver(notification);
        return notification;
    }
}
