package com.lumera.store.lumera_store_api.order.adapter.out.persistence;

import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(Order order) {
        var existing = repository.findById(order.getId());
        if (existing.isPresent()) {
            OrderEntity entity = existing.get();
            entity.updateStatus(order.getStatus());
            return toDomain(entity);
        }

        OrderEntity entity = toEntity(order);
        OrderEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return repository.findById(id)
                .map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Order> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .map(this::toDomain);
    }

    private OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity(
                order.getId(),
                order.getCreatedAt(),
                order.getStatus()
        );

        for (int position = 0; position < order.getItems().size(); position++) {
            OrderItem item = order.getItems().get(position);

            entity.addItem(new OrderItemEntity(
                    position,
                    item.productId(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice()
            ));
        }

        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        var items = entity.getItems().stream()
                .map(item -> new OrderItem(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();
        return Order.restore(
                entity.getId(),
                items,
                entity.getCreatedAt(),
                entity.getStatus()
        );
    }
}

