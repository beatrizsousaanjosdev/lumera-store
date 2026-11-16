package com.lumera.store.lumera_store_api.order.application.event;

import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockFinalizationRequestedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        boolean confirm
) {
    public StockFinalizationRequestedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
    }

    public static StockFinalizationRequestedEvent create(Order order) {
        Objects.requireNonNull(order, "Order is required");

        if (order.getStatus() != OrderStatus.PENDING_STOCK_CONFIRMATION
                && order.getStatus() != OrderStatus.PENDING_STOCK_RELEASE) {
            throw new IllegalArgumentException(
                    "Order is not awaiting stock finalization"
            );
        }

        return new StockFinalizationRequestedEvent(
                UUID.randomUUID(),
                order.getId(),
                Instant.now(),
                order.getStatus() == OrderStatus.PENDING_STOCK_CONFIRMATION
        );
    }
}