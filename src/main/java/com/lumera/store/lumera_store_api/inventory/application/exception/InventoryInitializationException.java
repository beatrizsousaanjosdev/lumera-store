package com.lumera.store.lumera_store_api.inventory.application.exception;

import java.util.UUID;

public class InventoryInitializationException extends RuntimeException {

    public InventoryInitializationException(UUID productId) {

        super("Inventory unavailable after initialization: " + productId );
    }
}
