package com.lumera.store.lumera_store_api.inventory.application.event;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockFinalizedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        boolean confirmed
) {

    public StockFinalizedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
    }

    public static StockFinalizedEvent create(StockReservation reservation) {
        Objects.requireNonNull(reservation, "Reservation is required");

        var status = reservation.getStatus();

        if (status != StockReservationStatus.CONFIRMED
                && status != StockReservationStatus.RELEASED) {
            throw new IllegalArgumentException(
                    "Reservation must be confirmed or released"
            );
        }

        return new StockFinalizedEvent(
                UUID.randomUUID(),
                reservation.getOrderId(),
                Instant.now(),
                status == StockReservationStatus.CONFIRMED
        );
    }
}
