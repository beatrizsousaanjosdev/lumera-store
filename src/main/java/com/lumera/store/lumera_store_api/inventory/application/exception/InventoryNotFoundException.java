package com.lumera.store.lumera_store_api.inventory.application.exception;

import java.util.UUID;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(UUID productId) {

        super("Inventory not found for product: " + productId);
    }
}
