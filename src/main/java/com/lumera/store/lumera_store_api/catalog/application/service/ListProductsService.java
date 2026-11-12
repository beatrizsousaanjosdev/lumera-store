package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.InvalidPaginationException;
import com.lumera.store.lumera_store_api.catalog.application.model.ProductPage;
import com.lumera.store.lumera_store_api.catalog.application.port.in.ListProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListProductsService implements ListProductsUsecase {
    private final ProductRepositoryPort port;

    @Override
    @Transactional(readOnly = true)
    public ProductPage execute(int page, int size, Category category, Boolean active) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPaginationException();
        }
        log.debug("Listing products: page={}, size={}, category={}, active={}", page, size, category, active);
        return port.findAll(page, size, category, active);
    }
}
