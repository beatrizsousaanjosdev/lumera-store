package com.lumera.store.lumera_store_api.order.adapter.out.persistence;

import com.lumera.store.lumera_store_api.order.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPersistenceAdapterTest {
    private final OrderJpaRepository repository = mock(OrderJpaRepository.class);
    private final OrderPersistenceAdapter adapter = new OrderPersistenceAdapter(repository);

    private OrderEntity entity(UUID id) {
        var entity = new OrderEntity(id, Instant.now(), OrderStatus.PENDING_STOCK);
        entity.addItem(new OrderItemEntity(0, UUID.randomUUID(), "Mouse", 2, new BigDecimal("10.00")));
        return entity;
    }

    @Test
    void shouldUseOrdinaryLookupWithoutPessimisticLock() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entity(id)));
        assertEquals(id, adapter.findById(id).orElseThrow().getId());
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldUseLockingLookupWhenRequested() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity(id)));
        assertEquals(id, adapter.findByIdForUpdate(id).orElseThrow().getId());
        verify(repository).findByIdForUpdate(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldMapAndAttachAllNewItemsInOrder() {
        var order = new Order(List.of(new OrderItem(UUID.randomUUID(), "Mouse", 2, BigDecimal.TEN),
                new OrderItem(UUID.randomUUID(), "Keyboard", 1, BigDecimal.ONE)));
        when(repository.findById(order.getId())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(order.getItems(), adapter.save(order).getItems());
        var captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(repository).save(captor.capture());
        var entity = captor.getValue();
        assertEquals(2, entity.getItems().size());
        assertSame(entity, entity.getItems().getFirst().getOrder());
        assertEquals(0, entity.getItems().getFirst().getPosition());
        assertEquals(1, entity.getItems().getLast().getPosition());
    }

    @Test
    void shouldUpdateOnlyStatusForExistingOrder() {
        UUID id = UUID.randomUUID();
        var entity = entity(id);
        var item = entity.getItems().getFirst();
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        var order = Order.restore(id, List.of(new OrderItem(item.getProductId(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice())), entity.getCreatedAt(), OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, adapter.save(order).getStatus());
        assertSame(item, entity.getItems().getFirst());
        verify(repository, never()).save(any());
    }
}
