package com.lumera.store.lumera_store_api.order.application.port.out;

import com.lumera.store.lumera_store_api.order.domain.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    Order save(Order order);
    Optional<Order> findById(UUID id);
    Optional<Order> findByIdForUpdate(UUID id);
}
