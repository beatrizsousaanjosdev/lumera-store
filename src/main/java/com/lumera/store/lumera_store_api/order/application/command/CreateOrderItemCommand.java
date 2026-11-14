package com.lumera.store.lumera_store_api.order.application.command;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;

import java.util.UUID;

public record CreateOrderItemCommand(UUID productId, int quantity) {

    public  CreateOrderItemCommand {
        if (productId == null) throw new InvalidOrderException("productId cannot be null");
        if (quantity <= 0) {
            throw new InvalidOrderException("Item quantity must be greater than zero");
        }

    }
}
