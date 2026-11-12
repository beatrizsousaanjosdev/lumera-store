package com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto;

import java.math.BigDecimal;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import java.util.UUID;

public record ProductResponseDto(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Category category,
        boolean active

) {
}
