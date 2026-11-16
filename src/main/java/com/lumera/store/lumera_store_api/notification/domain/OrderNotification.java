package com.lumera.store.lumera_store_api.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderNotification(UUID id, UUID eventId, UUID orderId,
                                NotificationOutcome outcome, Instant processedAt) {
    public OrderNotification {
        Objects.requireNonNull(id, "Notification ID is required");
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(outcome, "Order outcome is required");
        Objects.requireNonNull(processedAt, "Processing timestamp is required");
    }

    public String message() {
        return switch (outcome) {
            case CONFIRMED -> "Your order " + orderId + " has been confirmed.";
            case CANCELLED -> "Your order " + orderId + " has been cancelled.";
        };
    }
}
