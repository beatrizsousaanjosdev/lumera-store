package com.lumera.store.lumera_store_api.inventory.application.port.out;

import com.lumera.store.lumera_store_api.inventory.application.event.StockReservationResultEvent;

public interface StockReservationEventOutboxPort {
    void append(StockReservationResultEvent event);
}
