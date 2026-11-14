package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.*;
import com.lumera.store.lumera_store_api.order.application.exception.*;
import com.lumera.store.lumera_store_api.order.application.model.ProductSnapshot;
import com.lumera.store.lumera_store_api.order.application.port.out.*;
import com.lumera.store.lumera_store_api.order.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServicesTest {
    private final OrderRepositoryPort repository = mock(OrderRepositoryPort.class);
    private final ProductCatalogPort catalog = mock(ProductCatalogPort.class);
    private final OrderEventOutboxPort eventOutbox =
            mock(OrderEventOutboxPort.class);
    private final CreateOrderService create = new CreateOrderService(repository, catalog, eventOutbox);

    @Test
    void shouldUseCatalogSnapshotsAndReturnSavedOrder() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(catalog.findById(first)).thenReturn(Optional.of(new ProductSnapshot(first, "Mouse", new BigDecimal("10.25"), true)));
        when(catalog.findById(second)).thenReturn(Optional.of(new ProductSnapshot(second, "Keyboard", new BigDecimal("2.50"), true)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = create.execute(new CreateOrderCommand(List.of(
                new CreateOrderItemCommand(first, 2), new CreateOrderItemCommand(second, 3))));
        var captor = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(captor.capture());
        assertSame(result, captor.getValue());
        assertEquals(OrderStatus.PENDING_STOCK, result.getStatus());
        assertEquals(new BigDecimal("28.00"), result.getTotal());
        assertEquals("Mouse", result.getItems().getFirst().productName());
        assertEquals(new BigDecimal("10.25"), result.getItems().getFirst().unitPrice());
        verify(catalog).findById(first);
        verify(catalog).findById(second);
    }

    @Test
    void shouldNotSaveWhenAnyProductIsMissing() {
        UUID first = UUID.randomUUID(), missing = UUID.randomUUID();
        when(catalog.findById(first)).thenReturn(Optional.of(new ProductSnapshot(first, "Mouse", BigDecimal.TEN, true)));
        when(catalog.findById(missing)).thenReturn(Optional.empty());
        assertThrows(OrderProductNotFoundException.class, () -> create.execute(new CreateOrderCommand(List.of(
                new CreateOrderItemCommand(first, 1), new CreateOrderItemCommand(missing, 1)))));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldNotSaveInactiveProduct() {
        UUID id = UUID.randomUUID();
        when(catalog.findById(id)).thenReturn(Optional.of(new ProductSnapshot(id, "Mouse", BigDecimal.TEN, false)));
        assertThrows(InactiveProductException.class, () -> create.execute(new CreateOrderCommand(
                List.of(new CreateOrderItemCommand(id, 1)))));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldFindExistingOrderWithoutWriteLock() {
        var order = new Order(List.of(new OrderItem(UUID.randomUUID(), "Mouse", 1, BigDecimal.TEN)));
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));
        assertSame(order, new FindOrderByIdService(repository).execute(order.getId()));
        verify(repository).findById(order.getId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldRejectMissingOrderAndNullId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        var service = new FindOrderByIdService(repository);
        assertThrows(OrderNotFoundException.class, () -> service.execute(id));
        assertThrows(NullPointerException.class, () -> service.execute(null));
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }
}
