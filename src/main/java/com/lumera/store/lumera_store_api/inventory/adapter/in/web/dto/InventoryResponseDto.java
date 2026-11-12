package com.lumera.store.lumera_store_api.inventory.adapter.in.web.dto;

import java.util.UUID;

public record InventoryResponseDto(
        UUID productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity
) {
}
