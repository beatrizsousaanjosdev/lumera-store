package com.lumera.store.lumera_store_api.catalog.application.port.in;

import com.lumera.store.lumera_store_api.catalog.domain.Product;

import java.util.UUID;

public interface FindProductByIdUsecase {

    Product execute(UUID id);
}
