package com.lumera.store.lumera_store_api.notification.adapter.out.delivery;

import com.lumera.store.lumera_store_api.notification.application.port.out.NotificationDeliveryPort;
import com.lumera.store.lumera_store_api.notification.domain.OrderNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class LoggingNotificationDeliveryAdapter implements NotificationDeliveryPort {
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deliver(OrderNotification notification) {
        // This is a local simulation: the database record is authoritative.
        // Log only after commit so a rolled-back transaction never announces success.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Simulated order notification: notificationId={}, orderId={}, outcome={}, message={}",
                        notification.id(), notification.orderId(), notification.outcome(), notification.message());
            }
        });
    }
}
