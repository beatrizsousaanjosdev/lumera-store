package com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto;


import jakarta.validation.constraints.*;
import com.lumera.store.lumera_store_api.catalog.domain.Category;

import java.math.BigDecimal;

public record UpdateProductRequesDto(
        @NotBlank
        @Size(max = 150)
        String name,

        String description,

        @NotNull
        @DecimalMin(
                value = "0",
                inclusive = false,
                message = "The price must be greater than zero."
        )
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,

        @NotNull
        Category category
) {
}
