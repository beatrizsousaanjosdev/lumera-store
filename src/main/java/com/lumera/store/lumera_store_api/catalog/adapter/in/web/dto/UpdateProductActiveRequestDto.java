package com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductActiveRequestDto(@NotNull Boolean active) {
}
