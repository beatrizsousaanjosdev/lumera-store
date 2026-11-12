package com.lumera.store.lumera_store_api.catalog.application.model;

import com.lumera.store.lumera_store_api.catalog.domain.Product;
import java.util.List;

public record ProductPage(
        List<Product> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ProductPage {
        content = List.copyOf(content);
    }
}
