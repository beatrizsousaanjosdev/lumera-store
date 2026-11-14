package com.lumera.store.lumera_store_api.order.application.port.out;

import com.lumera.store.lumera_store_api.order.application.model.ProductSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface ProductCatalogPort {

    Optional<ProductSnapshot> findById(UUID productId);
}
