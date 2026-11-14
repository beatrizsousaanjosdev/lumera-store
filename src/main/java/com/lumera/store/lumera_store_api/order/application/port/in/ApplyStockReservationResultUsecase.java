package com.lumera.store.lumera_store_api.order.application.port.in;

import com.lumera.store.lumera_store_api.order.application.command.StockReservationResultCommand;
import com.lumera.store.lumera_store_api.order.domain.Order;

public interface ApplyStockReservationResultUsecase {
    Order execute(StockReservationResultCommand command);
}
