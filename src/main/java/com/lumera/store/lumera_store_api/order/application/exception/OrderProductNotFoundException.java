package com.lumera.store.lumera_store_api.order.application.exception;

import java.util.UUID;

public class OrderProductNotFoundException extends RuntimeException {

    public OrderProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
    }
}
