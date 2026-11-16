package com.lumera.store.lumera_store_api.order.application.command;

import java.util.Objects;
import java.util.UUID;

public record CompleteOrderStockCommand(
        UUID orderId,
        boolean confirmed
) {
    public CompleteOrderStockCommand {
        Objects.requireNonNull(orderId, "Order ID is required");
    }
}