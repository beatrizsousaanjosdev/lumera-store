package com.lumera.store.lumera_store_api.order.application.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {

        super("Order not found: " + orderId);
    }
}
