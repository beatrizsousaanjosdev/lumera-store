package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateProductActiveServiceTest {
    private final ProductRepositoryPort port = mock(ProductRepositoryPort.class);
    private final UpdateProductActiveService service = new UpdateProductActiveService(port);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUpdateStatusAndAllowRepeatingIt(boolean active) {
        var product = new Product("Mouse", "USB", BigDecimal.TEN, Category.PERIPHERALS);
        UUID id = product.getId();
        when(port.findById(id)).thenReturn(Optional.of(product));
        when(port.save(product)).thenReturn(product);
        assertEquals(active, service.execute(id, active).isActive());
        assertEquals(active, service.execute(id, active).isActive());
        assertEquals(id, product.getId());
        assertEquals("Mouse", product.getName());
        assertEquals("USB", product.getDescription());
        assertEquals(BigDecimal.TEN, product.getPrice());
        assertEquals(Category.PERIPHERALS, product.getCategory());
    }

    @Test
    void shouldNotSaveMissingProduct() {
        UUID id = UUID.randomUUID();
        when(port.findById(id)).thenReturn(Optional.empty());
        assertThrows(ProductNotFoundException.class, () -> service.execute(id, false));
        verify(port, never()).save(any());
    }
}
