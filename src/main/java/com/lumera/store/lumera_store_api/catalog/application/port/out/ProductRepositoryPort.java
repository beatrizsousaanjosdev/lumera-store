package com.lumera.store.lumera_store_api.catalog.application.port.out;

import com.lumera.store.lumera_store_api.catalog.application.model.ProductPage;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {

    Product save(Product product);
    Optional<Product> findById(UUID id);
    ProductPage findAll(int page, int size, Category category, Boolean active);

}
