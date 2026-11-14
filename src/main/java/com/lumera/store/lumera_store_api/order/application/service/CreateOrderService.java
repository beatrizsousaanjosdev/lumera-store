package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.CreateOrderCommand;
import com.lumera.store.lumera_store_api.order.application.command.CreateOrderItemCommand;
import com.lumera.store.lumera_store_api.order.application.exception.InactiveProductException;
import com.lumera.store.lumera_store_api.order.application.exception.OrderProductNotFoundException;
import com.lumera.store.lumera_store_api.order.application.port.in.CreateOrderUsecase;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.application.port.out.ProductCatalogPort;
import com.lumera.store.lumera_store_api.order.application.event.OrderCreatedEvent;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderEventOutboxPort;
import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUsecase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductCatalogPort productCatalogPort;
    private final OrderEventOutboxPort eventOutboxPort;

    @Override
    @Transactional
    public Order execute(CreateOrderCommand command) {
        var items = command.items().stream()
                .map(this::createItem)
                .toList();

        var order = new Order(items);
        var saved = orderRepositoryPort.save(order);

        var event = OrderCreatedEvent.create(saved);
        eventOutboxPort.append(event);

        log.debug(
                "Order and event staged: orderId={}, eventId={}",
                saved.getId(),
                event.eventId()
        );

        return saved;
    }

    private OrderItem createItem(CreateOrderItemCommand item) {
        var product = productCatalogPort.findById(item.productId())
                .orElseThrow(
                        () -> new OrderProductNotFoundException(item.productId())
                );

        if (!product.active()) throw new InactiveProductException(item.productId());

        return  new OrderItem(
                product.id(),
                product.name(),
                item.quantity(),
                product.price()
        );
    }
}
