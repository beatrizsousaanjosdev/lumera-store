package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockFinalizationRequestedMessage(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        Boolean confirm
) {
    public StockFinalizationRequestedMessage {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        Objects.requireNonNull(confirm, "Finalization action is required");
    }
}