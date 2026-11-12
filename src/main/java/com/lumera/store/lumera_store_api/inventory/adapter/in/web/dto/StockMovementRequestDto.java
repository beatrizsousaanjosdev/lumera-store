package com.lumera.store.lumera_store_api.inventory.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockMovementRequestDto(
        @NotNull
        @Positive(message = "Amount must be greater than zero")
        Integer amount
) {
}
