package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import com.lumera.store.lumera_store_api.catalog.domain.exception.InvalidProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateProductServiceTest {

    private ProductRepositoryPort port;
    private UpdateProductService service;

    @BeforeEach
    void setUp() {
        port = mock(ProductRepositoryPort.class);
        service = new UpdateProductService(port);
    }

    @Test
    void shouldUpdateExistingProduct() {
        UUID id = UUID.randomUUID();

        Product product = Product.restore(
                id, "Teclado", "Original", new BigDecimal("249.90"), Category.ACCESSORIES, false
        );

        when(port.findById(id)).thenReturn(Optional.of(product));
        when(port.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = service.execute(
                id, "Mouse", "Sem fio", new BigDecimal("129.90"), Category.PERIPHERALS
        );

        assertAll(
                () -> assertEquals(id, result.getId()),
                () -> assertEquals("Mouse", result.getName()),
                () -> assertEquals("Sem fio", result.getDescription()),
                () -> assertEquals(
                        new BigDecimal("129.90"), result.getPrice()
                ),
                () -> assertEquals(Category.PERIPHERALS, result.getCategory()),
                () -> assertFalse(result.isActive())
        );

        verify(port).save(product);
    }

    @Test
    void shouldNotSaveWhenProductDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(port.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> service.execute(
                        id, "Mouse", null, new BigDecimal("129.90"),Category.ACCESSORIES
                )
        );

        verify(port, never()).save(any(Product.class));
    }

    @Test
    void shouldNotSaveOrChangeProductWhenPriceIsInvalid() {
        Product product = new Product(
                "Teclado",
                "Original",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
                );

        when(port.findById(product.getId()))
                .thenReturn(Optional.of(product));

        assertThrows(
                InvalidProductException.class,
                () -> service.execute(
                        product.getId(), "Mouse", "Nova", BigDecimal.ZERO, Category.ACCESSORIES
                )
        );

        verify(port, never()).save(any(Product.class));

        assertAll(
                () -> assertEquals("Teclado", product.getName()),
                () -> assertEquals("Original", product.getDescription()),
                () -> assertEquals(
                        new BigDecimal("249.90"), product.getPrice()
                )
        );
    }
}