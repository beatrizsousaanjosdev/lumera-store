package com.lumera.store.lumera_store_api.catalog.application.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found by ID: " + id);
    }
}
