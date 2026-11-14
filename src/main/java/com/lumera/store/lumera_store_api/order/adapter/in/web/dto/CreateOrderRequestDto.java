package com.lumera.store.lumera_store_api.order.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequestDto(
        @NotEmpty
        List<@NotNull @Valid Item> items
) {

    @Schema(name = "CreateOrderItemRequest")
    public record Item(
            @NotNull UUID productId,
            @NotNull @Positive Integer quantity
            ) {
    }
}
