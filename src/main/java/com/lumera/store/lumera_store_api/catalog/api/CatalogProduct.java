package com.lumera.store.lumera_store_api.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogProduct(
        UUID id,
        String name,
        BigDecimal price,
        boolean active
) {
}
