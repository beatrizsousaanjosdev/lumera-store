package com.lumera.store.lumera_store_api.catalog.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID eventId,
        UUID productId,
        Instant occurredAt
) {

    public ProductCreatedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(productId, "Product ID is required");
        Objects.requireNonNull(occurredAt, "Event timestamp is required");
    }

    public static ProductCreatedEvent create(UUID productId) {
        return new ProductCreatedEvent(
                UUID.randomUUID(),
                productId,
                Instant.now()
        );
    }

}
