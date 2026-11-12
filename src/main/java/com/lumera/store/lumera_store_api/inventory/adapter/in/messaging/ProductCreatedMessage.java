package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

public record ProductCreatedMessage(
        UUID eventId,
        UUID productId,
        Instant occurredAt
) {
}
