package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockReservationResultMessage(UUID eventId, UUID orderId, Instant occurredAt,
                                            Boolean reserved, String rejectionReason) {
    public StockReservationResultMessage {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        Objects.requireNonNull(reserved, "Reservation result is required");
        if (reserved ? rejectionReason != null : rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must match reservation result");
        }
    }
}
