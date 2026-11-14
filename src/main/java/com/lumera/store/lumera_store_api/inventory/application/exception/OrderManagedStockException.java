package com.lumera.store.lumera_store_api.inventory.application.exception;

import java.util.UUID;

public class OrderManagedStockException extends RuntimeException {
    public OrderManagedStockException(UUID productId) {
        super("Use the order reservation workflow while product has active order reservations: " + productId);
    }
}
