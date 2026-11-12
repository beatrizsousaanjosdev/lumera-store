package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.application.port.in.UpdateProductUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProductService implements UpdateProductUsecase {

    private final ProductRepositoryPort port;

    @Override
    @Transactional
    public Product execute(UUID id, String name, String description, BigDecimal price, Category category) {
        log.debug("Updating product: productId={}", id);

        Product product = port.findById(id)
                .orElseThrow(() -> {
                    log.debug("Product not found: productId={}", id);
                    return new ProductNotFoundException(id);
                });

        product.updateDetails(name, description, price, category);

        Product result = port.save(product);

        log.debug("Product updated: productId={}", product.getId());

        return result;
    }
}
