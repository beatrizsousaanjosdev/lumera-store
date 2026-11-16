package com.lumera.store.lumera_store_api.notification.adapter.in.messaging;

import com.lumera.store.lumera_store_api.notification.domain.NotificationOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderCompletedMessage(UUID eventId, UUID orderId, Instant occurredAt, NotificationOutcome status) {
    public OrderCompletedMessage {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        Objects.requireNonNull(status, "Final order status is required");
    }
}
