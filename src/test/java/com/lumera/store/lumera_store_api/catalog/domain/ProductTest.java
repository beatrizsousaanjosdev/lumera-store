package com.lumera.store.lumera_store_api.catalog.domain;

import com.lumera.store.lumera_store_api.catalog.domain.exception.InvalidProductException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    void shouldCreateActiveProductWithGeneratedId() {
        Product product = new Product(
                "  Teclado  ",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        assertAll(
                () -> assertNotNull(product.getId()),
                () -> assertEquals("Teclado", product.getName()),
                () -> assertEquals(
                        new BigDecimal("249.90"), product.getPrice()
                ),
                () -> assertTrue(product.isActive())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void shouldRejectInvalidName(String name) {
        assertThrows(
                InvalidProductException.class,
                () -> new Product(name, null, BigDecimal.ONE, Category.ACCESSORIES)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100"})
    void shouldRejectInvalidPrice(String value) {
        BigDecimal price = value == null
                ? null
                : new BigDecimal(value);

        assertThrows(
                InvalidProductException.class,
                () -> new Product("Teclado", null, price, Category.ACCESSORIES)
        );
    }

    @Test
    void shouldRestoreExistingIdAndInactiveStatus() {
        UUID id = UUID.randomUUID();

        Product product = Product.restore(
                id,
                "Teclado",
                null,
                new BigDecimal("249.90"),
                Category.ACCESSORIES,
                false
        );

        assertAll(
                () -> assertEquals(id, product.getId()),
                () -> assertFalse(product.isActive())
        );
    }

    @Test
    void shouldUpdateDetailsPreservingIdAndActiveStatus() {
        UUID id = UUID.randomUUID();

        Product product = Product.restore(
                id,
                "Teclado",
                "Descrição original",
                new BigDecimal("249.90"),
                Category.ACCESSORIES,
                false
        );

        product.updateDetails(
                "  Mouse  ",
                "Mouse sem fio",
                new BigDecimal("129.90"),
                Category.ACCESSORIES
        );

        assertAll(
                () -> assertEquals(id, product.getId()),
                () -> assertFalse(product.isActive()),
                () -> assertEquals("Mouse", product.getName()),
                () -> assertEquals("Mouse sem fio", product.getDescription()),
                () -> assertEquals(
                        new BigDecimal("129.90"), product.getPrice()
                )
        );
    }

    @Test
    void shouldPreserveDetailsWhenUpdateIsInvalid() {
        Product product = new Product(
                "Teclado",
                "Descrição original",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        assertThrows(
                InvalidProductException.class,
                () -> product.updateDetails(
                        "Mouse",
                        "Nova descrição",
                        new BigDecimal("-10.00"),
                        Category.ACCESSORIES
                )
        );

        assertAll(
                () -> assertEquals("Teclado", product.getName()),
                () -> assertEquals(
                        "Descrição original", product.getDescription()
                ),
                () -> assertEquals(
                        new BigDecimal("249.90"), product.getPrice()
                )
        );
    }

    @Test
    void shouldRejectNameLongerThan150Characters() {
        assertThrows(
                InvalidProductException.class,
                () -> new Product("a".repeat(151), null, BigDecimal.ONE, Category.ACCESSORIES)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.999", "100000000000000000.00"})
    void shouldRejectPriceOutsidePersistenceLimits(String value) {
        assertThrows(
                InvalidProductException.class,
                () -> new Product("Teclado", null, new BigDecimal(value), Category.ACCESSORIES)
        );
    }

    @Test
    void shouldRejectMissingCategory() {
        assertThrows(InvalidProductException.class,
                () -> new Product("Teclado", null, BigDecimal.ONE, null));
    }

    @Test
    void shouldUpdateCategoryAndPreserveDetailsWhenCategoryIsMissing() {
        Product product = new Product("Teclado", "Original", BigDecimal.ONE, Category.OTHER);
        product.updateDetails("Mouse", "Sem fio", BigDecimal.TEN, Category.PERIPHERALS);
        assertEquals(Category.PERIPHERALS, product.getCategory());

        assertThrows(InvalidProductException.class,
                () -> product.updateDetails("Monitor", "Nova", BigDecimal.ONE, null));
        assertAll(
                () -> assertEquals("Mouse", product.getName()),
                () -> assertEquals("Sem fio", product.getDescription()),
                () -> assertEquals(BigDecimal.TEN, product.getPrice()),
                () -> assertEquals(Category.PERIPHERALS, product.getCategory())
        );
    }

}