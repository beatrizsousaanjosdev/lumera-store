package com.lumera.store.lumera_store_api.notification.application.exception;

import java.util.UUID;

public class ConflictingNotificationException extends RuntimeException {
    public ConflictingNotificationException(UUID orderId) {
        super("Order already has a notification with a different outcome: " + orderId);
    }
}
