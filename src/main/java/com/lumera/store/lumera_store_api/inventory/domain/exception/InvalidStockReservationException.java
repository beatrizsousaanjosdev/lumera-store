package com.lumera.store.lumera_store_api.inventory.domain.exception;

public class InvalidStockReservationException extends RuntimeException {
    public InvalidStockReservationException(String message) {
        super(message);
    }
}
