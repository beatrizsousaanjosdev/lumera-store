package com.lumera.store.lumera_store_api.order.adapter.out.catalog;

import com.lumera.store.lumera_store_api.catalog.api.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCatalogAdapterTest {
    @Test
    void shouldMapPublicCatalogContractWithoutFilteringInactiveProducts() {
        var query = mock(CatalogQuery.class);
        UUID id = UUID.randomUUID();
        when(query.findById(id)).thenReturn(Optional.of(new CatalogProduct(id, "Mouse", BigDecimal.TEN, false)));
        var result = new ProductCatalogAdapter(query).findById(id).orElseThrow();
        assertEquals(id, result.id());
        assertEquals("Mouse", result.name());
        assertEquals(BigDecimal.TEN, result.price());
        assertFalse(result.active());
    }

    @Test
    void shouldPreserveAbsence() {
        var query = mock(CatalogQuery.class);
        UUID id = UUID.randomUUID();
        when(query.findById(id)).thenReturn(Optional.empty());
        assertTrue(new ProductCatalogAdapter(query).findById(id).isEmpty());
    }
}
