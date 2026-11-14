package com.lumera.store.lumera_store_api.order.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(
        UUID id,
        String name,
        BigDecimal price,
        boolean active
) {
}
