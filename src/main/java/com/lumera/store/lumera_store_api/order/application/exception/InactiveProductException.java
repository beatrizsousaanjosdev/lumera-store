package com.lumera.store.lumera_store_api.order.application.exception;

import java.util.UUID;

public class InactiveProductException extends RuntimeException {
    public InactiveProductException(UUID productId) {

        super("Cannot order an inactive product: " + productId);
    }
}
