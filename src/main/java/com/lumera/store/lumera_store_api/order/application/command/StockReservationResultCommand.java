package com.lumera.store.lumera_store_api.order.application.command;

import java.util.Objects;
import java.util.UUID;

public record StockReservationResultCommand(UUID orderId, boolean reserved, String rejectionReason) {
    public StockReservationResultCommand {
        Objects.requireNonNull(orderId, "Order ID is required");
        if (reserved ? rejectionReason != null : rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must match reservation result");
        }
    }
}
