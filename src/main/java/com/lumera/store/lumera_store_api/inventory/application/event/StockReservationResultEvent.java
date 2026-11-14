package com.lumera.store.lumera_store_api.inventory.application.event;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockReservationResultEvent(UUID eventId, UUID orderId, Instant occurredAt,
                                          boolean reserved, String rejectionReason) {
    public StockReservationResultEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        if (reserved ? rejectionReason != null : rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must match reservation result");
        }
    }
    public static StockReservationResultEvent create(StockReservation reservation) {
        if (reservation.getStatus() != StockReservationStatus.RESERVED
                && reservation.getStatus() != StockReservationStatus.REJECTED) {
            throw new IllegalArgumentException("Only initial reservation decisions produce this event");
        }
        return new StockReservationResultEvent(UUID.randomUUID(), reservation.getOrderId(), Instant.now(),
                reservation.getStatus() == StockReservationStatus.RESERVED, reservation.getRejectionReason());
    }
}
