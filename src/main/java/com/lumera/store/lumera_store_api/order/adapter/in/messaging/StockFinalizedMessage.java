package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockFinalizedMessage(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        Boolean confirmed
) {
    public StockFinalizedMessage {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        Objects.requireNonNull(confirmed, "Stock outcome is required");
    }
}