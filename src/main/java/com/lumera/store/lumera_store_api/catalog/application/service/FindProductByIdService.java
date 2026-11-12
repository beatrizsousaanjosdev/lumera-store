package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.application.port.in.FindProductByIdUsecase;
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
public class FindProductByIdService implements FindProductByIdUsecase {

    private final ProductRepositoryPort port;

    @Override
    @Transactional(readOnly = true)
    public Product execute(UUID id) {
        log.debug("Finding product: productId={}", id);

        Product product = port.findById(id)
                .orElseThrow(() -> {
                    log.debug("Product not found: productId={}", id);
                    return new ProductNotFoundException(id);
                });
        log.debug("Product found: productId={}", product.getId());

        return product;
    }
}
