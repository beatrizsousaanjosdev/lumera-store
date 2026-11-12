package com.lumera.store.lumera_store_api.catalog.application.port.out;

import com.lumera.store.lumera_store_api.catalog.application.event.ProductCreatedEvent;

public interface ProductEventOutboxPort {
    void append(ProductCreatedEvent event);
}
