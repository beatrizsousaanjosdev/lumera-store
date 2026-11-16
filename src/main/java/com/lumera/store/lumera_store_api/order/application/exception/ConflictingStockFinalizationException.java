package com.lumera.store.lumera_store_api.order.application.exception;

import java.util.UUID;

public class ConflictingStockFinalizationException extends RuntimeException {

    public ConflictingStockFinalizationException(UUID orderId) {
        super("Stock finalization conflicts with order: " + orderId);
    }
}