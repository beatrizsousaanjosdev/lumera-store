package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FindProductByIdServiceTest {

    private ProductRepositoryPort port;
    private FindProductByIdService service;

    @BeforeEach
    void setUp() {
        port = mock(ProductRepositoryPort.class);
        service = new FindProductByIdService(port);
    }

    @Test
    void shouldReturnProductWhenFound() {
        Product product = new Product(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        when(port.findById(product.getId()))
                .thenReturn(Optional.of(product));

        Product result = service.execute(product.getId());

        assertSame(product, result);
        verify(port).findById(product.getId());
    }

    @Test
    void shouldThrowWhenProductIsNotFound() {
        UUID id = UUID.randomUUID();

        when(port.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> service.execute(id)
        );

        verify(port).findById(id);
    }
}