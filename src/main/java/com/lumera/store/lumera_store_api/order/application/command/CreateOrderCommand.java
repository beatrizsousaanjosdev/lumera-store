package com.lumera.store.lumera_store_api.order.application.command;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        List<CreateOrderItemCommand> items
) {

    public CreateOrderCommand {

        if (items == null || items.isEmpty()) throw new InvalidOrderException("Order must contain at least one item");

        var productIds = new HashSet< UUID >();

        for (var item : items) {
            if (item == null) throw new InvalidOrderException("Order items cannot be null.");

            if (!productIds.add(item.productId())) throw  new InvalidOrderException("Order cannot contain duplicate product IDs.");
        }

        items = List.copyOf(items);
    }
}
