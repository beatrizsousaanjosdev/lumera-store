package com.lumera.store.lumera_store_api.catalog.api;

import java.util.Optional;
import java.util.UUID;

public interface CatalogQuery {

    Optional<CatalogProduct> findById(UUID productId);
}
