package com.lumera.store.lumera_store_api.inventory.application.port.in;

import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;

public interface StockMovementUsecase {

    Inventory execute(StockMovementCommand command);
}
