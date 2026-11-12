package com.lumera.store.lumera_store_api.inventory.domain.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(int requested, int available) {

        super("Insufficient stock: requested=%d, available=%d".formatted(requested, available));
    }
}
