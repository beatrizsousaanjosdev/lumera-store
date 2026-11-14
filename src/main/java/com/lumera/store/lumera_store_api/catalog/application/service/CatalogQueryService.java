package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.api.CatalogProduct;
import com.lumera.store.lumera_store_api.catalog.api.CatalogQuery;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogQueryService implements CatalogQuery {

    private final ProductRepositoryPort productRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogProduct> findById(UUID productId) {
        return productRepositoryPort.findById(productId)
                .map(product -> new CatalogProduct(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.isActive()
                ));
    }
}
