package com.lumera.store.lumera_store_api.inventory.application.command;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservationItem;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record ReserveOrderStockCommand(
        UUID orderId,
        List<StockReservationItem> items
) {

    public ReserveOrderStockCommand {
        if (orderId == null) {
            throw new InvalidStockReservationException(
                    "Order ID is required"
            );
        }

        if (items == null || items.isEmpty()) {
            throw new InvalidStockReservationException(
                    "Reservation must contain at least one item"
            );
        }

        var productIds = new HashSet<UUID>();

        for (var item : items) {
            if (item == null) {
                throw new InvalidStockReservationException(
                        "Reservation items cannot be null"
                );
            }

            if (!productIds.add(item.productId())) {
                throw new InvalidStockReservationException(
                        "Reservation cannot contain duplicate products"
                );
            }
        }

        items = List.copyOf(items);
    }
}