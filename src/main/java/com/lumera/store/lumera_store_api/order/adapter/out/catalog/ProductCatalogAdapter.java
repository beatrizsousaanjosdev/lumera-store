package com.lumera.store.lumera_store_api.order.adapter.out.catalog;

import com.lumera.store.lumera_store_api.catalog.api.CatalogQuery;
import com.lumera.store.lumera_store_api.order.application.model.ProductSnapshot;
import com.lumera.store.lumera_store_api.order.application.port.out.ProductCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductCatalogAdapter implements ProductCatalogPort {

    private final CatalogQuery catalogQuery;

    @Override
    public Optional<ProductSnapshot> findById(UUID productId) {
        return catalogQuery.findById(productId)
                .map(product -> new ProductSnapshot(
                        product.id(),
                        product.name(),
                        product.price(),
                        product.active()
                ));
    }
}
