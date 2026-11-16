package com.lumera.store.lumera_store_api.notification.application.port.out;

import com.lumera.store.lumera_store_api.notification.domain.OrderNotification;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {
    void lockByOrderId(UUID orderId);
    Optional<OrderNotification> findByOrderId(UUID orderId);
    void save(OrderNotification notification);
}
