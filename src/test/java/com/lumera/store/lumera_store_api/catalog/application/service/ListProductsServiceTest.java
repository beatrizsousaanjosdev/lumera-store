package com.lumera.store.lumera_store_api.catalog.application.service;

import com.lumera.store.lumera_store_api.catalog.application.exception.InvalidPaginationException;
import com.lumera.store.lumera_store_api.catalog.application.model.ProductPage;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListProductsServiceTest {
    private final ProductRepositoryPort port = mock(ProductRepositoryPort.class);
    private final ListProductsService service = new ListProductsService(port);

    @Test
    void shouldDelegatePaginationAndFilter() {
        var page = new ProductPage(List.of(), 1, 10, 0, 0);
        when(port.findAll(1, 10, Category.MONITORS, null)).thenReturn(page);
        assertSame(page, service.execute(1, 10, Category.MONITORS, null));
    }

    @Test
    void shouldAllowListingWithoutCategory() {
        var page = new ProductPage(List.of(), 0, 100, 0, 0);
        when(port.findAll(0, 100, null, null)).thenReturn(page);
        assertSame(page, service.execute(0, 100, null, null));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void shouldRejectInvalidPaginationBeforeQuerying(int page, int size) {
        assertThrows(InvalidPaginationException.class, () -> service.execute(page, size, null, null));
        verifyNoInteractions(port);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldForwardActiveFilter(boolean active) {
        var page = new ProductPage(List.of(), 0, 20, 0, 0);
        when(port.findAll(0, 20, Category.OTHER, active)).thenReturn(page);
        assertSame(page, service.execute(0, 20, Category.OTHER, active));
    }

}
