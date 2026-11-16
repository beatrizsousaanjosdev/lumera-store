package com.lumera.store.lumera_store_api.notification.application.port.out;

import com.lumera.store.lumera_store_api.notification.domain.OrderNotification;

public interface NotificationDeliveryPort {
    void deliver(OrderNotification notification);
}
