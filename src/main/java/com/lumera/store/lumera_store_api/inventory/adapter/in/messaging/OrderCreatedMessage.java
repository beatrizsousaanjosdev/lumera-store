package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderCreatedMessage(UUID eventId, UUID orderId, Instant occurredAt, List<Item> items) {
    public OrderCreatedMessage {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Order items are required");
        items = List.copyOf(items);
    }
    public record Item(UUID productId, int quantity) {
        public Item {
            Objects.requireNonNull(productId, "Product ID is required");
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
