package com.lumera.store.lumera_store_api.inventory.application.port.in;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;

import java.util.UUID;

public interface ReleaseOrderStockUsecase {

    StockReservation execute(UUID orderId);
}
