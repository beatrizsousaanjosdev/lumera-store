package com.lumera.store.lumera_store_api.catalog.application.port.in;

import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;

import java.math.BigDecimal;

public interface CreateProductsUsecase {

    Product execute(String name, String description, BigDecimal price, Category category);
}
