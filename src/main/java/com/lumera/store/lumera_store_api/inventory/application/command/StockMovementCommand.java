package com.lumera.store.lumera_store_api.inventory.application.command;

import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidInventoryException;

import java.util.UUID;

public record StockMovementCommand(UUID productId, int amount) {

    public StockMovementCommand {
        if (productId == null) {
            throw new InvalidInventoryException("Product ID is required");
        }
        if (amount <= 0) {
            throw new InvalidInventoryException("Stock movement amount must be greater than zero");
        }
    }
}
