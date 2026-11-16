package com.lumera.store.lumera_store_api.order.application.port.in;

import com.lumera.store.lumera_store_api.order.application.command.CompleteOrderStockCommand;
import com.lumera.store.lumera_store_api.order.domain.Order;

public interface CompleteOrderStockUsecase {

    Order execute(CompleteOrderStockCommand command);
}