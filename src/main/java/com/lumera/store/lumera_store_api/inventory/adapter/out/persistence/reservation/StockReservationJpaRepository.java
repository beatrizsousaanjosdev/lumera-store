package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.reservation;


import org.springframework.data.jpa.repository.JpaRepository;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationStatus;

import java.util.UUID;

public interface StockReservationJpaRepository
        extends JpaRepository<StockReservationEntity, UUID> {

    boolean existsByStatusAndItems_ProductId(StockReservationStatus status, UUID productId);
}

