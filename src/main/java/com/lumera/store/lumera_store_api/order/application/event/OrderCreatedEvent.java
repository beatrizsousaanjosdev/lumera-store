package com.lumera.store.lumera_store_api.order.application.event;

import com.lumera.store.lumera_store_api.order.domain.Order;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        List<Item> items
) {
    public OrderCreatedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Event timestamp is required");

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "Order event must contain at least one item"
            );
        }

        items = List.copyOf(items);
    }

    public static OrderCreatedEvent create(Order order) {
        Objects.requireNonNull(order, "Order is required");

        var items = order.getItems().stream()
                .map(item -> new Item(
                        item.productId(),
                        item.quantity()
                ))
                .toList();

        return new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                Instant.now(),
                items
        );
    }

    public record Item (UUID productId, int quantity) {

        public Item {
            Objects.requireNonNull(productId, "Product ID is required");

            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Item quantity must be greater than zero"
                );
            }
        }
    }
}
