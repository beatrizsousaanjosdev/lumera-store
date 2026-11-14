package com.lumera.store.lumera_store_api.inventory.domain;

import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;

import java.util.UUID;

public record StockReservationItem(
        UUID productId,
        int quantity
) {

    public StockReservationItem{
        if (productId == null) throw new InvalidStockReservationException("Product id cannot be null");

        if (quantity <= 0) throw new InvalidStockReservationException("Reservation quantity must be greater than zero");
    }
}
