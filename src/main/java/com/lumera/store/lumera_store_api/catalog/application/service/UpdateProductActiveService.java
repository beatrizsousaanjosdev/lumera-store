package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.application.port.in.UpdateProductActiveUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProductActiveService implements UpdateProductActiveUsecase {
    private final ProductRepositoryPort port;

    @Override
    @Transactional
    public Product execute(UUID id, boolean active) {
        log.debug("Updating product active status: productId={}, active={}", id, active);
        Product product = port.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.updateActive(active);
        return port.save(product);
    }
}
