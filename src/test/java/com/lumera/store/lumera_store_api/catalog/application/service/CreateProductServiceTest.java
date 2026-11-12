package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductEventOutboxPort;
import com.lumera.store.lumera_store_api.catalog.application.event.ProductCreatedEvent;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.exception.InvalidProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateProductServiceTest {


    private ProductRepositoryPort port;
    private ProductEventOutboxPort eventOutboxPort;
    private CreateProductService service;

    @BeforeEach
    void setUp() {
        port = mock(ProductRepositoryPort.class);
        eventOutboxPort = mock(ProductEventOutboxPort.class);

        service = new CreateProductService(port, eventOutboxPort);
    }

    @Test
    void shouldSaveProductAndReturnRepositoryResult() {
        Product savedProduct = new Product(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        when(port.save(any(Product.class))).thenReturn(savedProduct);

        Product result = service.execute(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        ArgumentCaptor<Product> captor =
                ArgumentCaptor.forClass(Product.class);

        verify(port).save(captor.capture());

        Product submitted = captor.getValue();
        var eventCaptor = ArgumentCaptor.forClass(ProductCreatedEvent.class);
        var order = inOrder(port, eventOutboxPort);
        order.verify(port).save(any(Product.class));
        order.verify(eventOutboxPort).append(eventCaptor.capture());
        assertEquals(savedProduct.getId(), eventCaptor.getValue().productId());
        assertNotNull(eventCaptor.getValue().eventId());
        assertNotNull(eventCaptor.getValue().occurredAt());

        assertAll(
                () -> assertEquals("Teclado", submitted.getName()),
                () -> assertEquals(
                        "Teclado USB", submitted.getDescription()
                ),
                () -> assertEquals(
                        new BigDecimal("249.90"), submitted.getPrice()
                ),
                () -> assertNotNull(submitted.getId()),
                () -> assertTrue(submitted.isActive()),
                () -> assertSame(savedProduct, result)
        );
    }

    @Test
    void shouldNotSaveProductWithInvalidName() {
        assertThrows(
                InvalidProductException.class,
                () -> service.execute(" ", null, BigDecimal.ONE, Category.ACCESSORIES)
        );

        verifyNoInteractions(port, eventOutboxPort);
    }

    @Test
    void shouldNotSaveProductWithInvalidPrice() {
        assertThrows(
                InvalidProductException.class,
                () -> service.execute("Teclado", null, BigDecimal.ZERO, Category.ACCESSORIES)
        );

        verifyNoInteractions(port, eventOutboxPort);
    }

    @Test
    void shouldPropagateOutboxFailure() {
        var product = new Product("Mouse", null, BigDecimal.TEN, Category.OTHER);
        when(port.save(any(Product.class))).thenReturn(product);
        doThrow(new IllegalStateException("outbox unavailable")).when(eventOutboxPort).append(any());
        assertThrows(IllegalStateException.class,
                () -> service.execute("Mouse", null, BigDecimal.TEN, Category.OTHER));
    }

    @Test
    void shouldNotAppendEventIfProductSaveFails() {
        when(port.save(any(Product.class))).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class,
                () -> service.execute("Mouse", null, BigDecimal.TEN, Category.OTHER));
        verifyNoInteractions(eventOutboxPort);
    }
}