package com.lumera.store.lumera_store_api.inventory.application.port.in;

import com.lumera.store.lumera_store_api.inventory.application.command.ReserveOrderStockCommand;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;

public interface ReserveOrderStockUsecase {

    StockReservation execute(ReserveOrderStockCommand command);
}