package com.lumera.store.lumera_store_api.order.application.port.in;

import com.lumera.store.lumera_store_api.order.domain.Order;

import java.util.UUID;

public interface FindOrderByIdUsecase {

    Order execute(UUID id);
}
