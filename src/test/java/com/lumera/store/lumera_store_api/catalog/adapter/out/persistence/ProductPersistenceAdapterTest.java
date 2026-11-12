package com.lumera.store.lumera_store_api.catalog.adapter.out.persistence;

import com.lumera.store.lumera_store_api.catalog.domain.Category;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductPersistenceAdapterTest {
    @ParameterizedTest
    @NullSource
    @EnumSource(Category.class)
    void shouldMapPageAndUseOptionalCategoryFilter(Category category) {
        var repository = mock(ProductRepository.class);
        var adapter = new ProductPersistenceAdapter(repository);
        var pageable = PageRequest.of(1, 2, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        UUID id = UUID.randomUUID();
        var entity = new ProductEntity(id, "Mouse", null, BigDecimal.TEN,
                category == null ? Category.OTHER : category, false);
        var page = new PageImpl<>(List.of(entity), pageable, 3);
        if (category == null) {
            when(repository.findAll(pageable)).thenReturn(page);
        } else {
            when(repository.findByCategory(category, pageable)).thenReturn(page);
        }

        var result = adapter.findAll(1, 2, category, null);
        assertAll(
                () -> assertEquals(1, result.page()),
                () -> assertEquals(2, result.size()),
                () -> assertEquals(3, result.totalElements()),
                () -> assertEquals(2, result.totalPages()),
                () -> assertEquals(id, result.content().getFirst().getId()),
                () -> assertEquals(entity.getCategory(), result.content().getFirst().getCategory()),
                () -> assertFalse(result.content().getFirst().isActive())
        );
        if (category == null) verify(repository).findAll(pageable);
        else verify(repository).findByCategory(category, pageable);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void shouldCombineOptionalCategoryAndActive(boolean filterCategory, boolean active) {
        var repository = mock(ProductRepository.class);
        var adapter = new ProductPersistenceAdapter(repository);
        var pageable = PageRequest.of(0, 20, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        Category category = filterCategory ? Category.OTHER : null;
        var page = new PageImpl<ProductEntity>(List.of(), pageable, 0);
        if (filterCategory) when(repository.findByCategoryAndActive(category, active, pageable)).thenReturn(page);
        else when(repository.findByActive(active, pageable)).thenReturn(page);
        assertTrue(adapter.findAll(0, 20, category, active).content().isEmpty());
        if (filterCategory) verify(repository).findByCategoryAndActive(category, active, pageable);
        else verify(repository).findByActive(active, pageable);
        verifyNoMoreInteractions(repository);
    }

}
