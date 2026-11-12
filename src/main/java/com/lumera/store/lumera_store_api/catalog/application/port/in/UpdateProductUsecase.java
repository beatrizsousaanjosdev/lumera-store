package com.lumera.store.lumera_store_api.catalog.application.port.in;

import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateProductUsecase {

    Product execute(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            Category category
    );
}
