package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.event.ProductCreatedEvent;
import com.lumera.store.lumera_store_api.catalog.application.port.in.CreateProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductEventOutboxPort;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateProductService implements CreateProductsUsecase {

    private final ProductRepositoryPort port;
    private final ProductEventOutboxPort eventOutboxPort;

    @Override
    @Transactional
    public Product execute(String name, String description, BigDecimal price, Category category) {
        log.debug("Starting the product registration process");

        Product product = Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .category(category)
                .build();

        Product saved = port.save(product);

        ProductCreatedEvent event = ProductCreatedEvent.create(
                saved.getId()
        );

        eventOutboxPort.append(event);

        log.debug(
                "Product and event staged: productId={}, eventId={}",
                saved.getId(),
                event.eventId()
        );

        return saved;
    }
}
