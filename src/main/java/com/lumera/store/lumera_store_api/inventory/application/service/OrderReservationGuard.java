package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.exception.OrderManagedStockException;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderReservationGuard {
    private final StockReservationRepositoryPort reservations;

    // Call only after locking the inventory row, to serialize with order-based movements.
    public void requireNoActiveOrderReservation(UUID productId) {
        if (reservations.hasActiveReservationForProduct(productId)) {
            throw new OrderManagedStockException(productId);
        }
    }
}
