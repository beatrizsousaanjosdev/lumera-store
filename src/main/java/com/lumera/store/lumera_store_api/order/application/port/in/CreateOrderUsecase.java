package com.lumera.store.lumera_store_api.order.application.port.in;

import com.lumera.store.lumera_store_api.order.application.command.CreateOrderCommand;
import com.lumera.store.lumera_store_api.order.domain.Order;

public interface CreateOrderUsecase {

    Order execute(CreateOrderCommand command);
}
