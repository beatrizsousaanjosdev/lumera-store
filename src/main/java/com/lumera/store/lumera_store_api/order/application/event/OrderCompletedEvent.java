package com.lumera.store.lumera_store_api.order.application.event;

import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderCompletedEvent(UUID eventId, UUID orderId, Instant occurredAt, OrderStatus status) {
    public OrderCompletedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Only completed orders can publish their outcome");
        }
    }

    public static OrderCompletedEvent create(Order order) {
        Objects.requireNonNull(order, "Order is required");
        return new OrderCompletedEvent(UUID.randomUUID(), order.getId(), Instant.now(), order.getStatus());
    }
}
