package com.lumera.store.lumera_store_api.catalog.application.port.in;

import com.lumera.store.lumera_store_api.catalog.application.model.ProductPage;
import com.lumera.store.lumera_store_api.catalog.domain.Category;

public interface ListProductsUsecase {
    ProductPage execute(int page, int size, Category category, Boolean active);
}
