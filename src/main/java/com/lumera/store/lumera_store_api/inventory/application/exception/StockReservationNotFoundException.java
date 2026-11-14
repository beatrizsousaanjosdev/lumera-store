package com.lumera.store.lumera_store_api.inventory.application.exception;

import java.util.UUID;

public class StockReservationNotFoundException extends RuntimeException {

    public StockReservationNotFoundException(UUID orderId) {

        super("Stock reservation not found for order: " + orderId);
    }
}
