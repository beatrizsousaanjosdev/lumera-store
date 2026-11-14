package com.lumera.store.lumera_store_api.inventory.application.port.out;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;

import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepositoryPort {

    void lockByOrderId(UUID orderId);

    boolean hasActiveReservationForProduct(UUID productId);

    Optional<StockReservation> findByOrderId(UUID orderId);

    StockReservation save(StockReservation stockReservation);
}
