package com.lumera.store.lumera_store_api.order.domain;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Getter
public class Order {

    private final UUID id;
    private final List<OrderItem> items;
    private final Instant createdAt;
    private OrderStatus status;

    public Order(List<OrderItem> items) {
        this(
                UUID.randomUUID(),
                items,
                Instant.now(),
                OrderStatus.PENDING_STOCK
        );
    }

    private Order(
            UUID id,
            List<OrderItem> items,
            Instant createdAt,
            OrderStatus status
    ) {

        if (id == null || createdAt == null || status == null) {
            throw new InvalidOrderException(
                    "Order ID, creation timestamp and status are required"
            );
        }

        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException(
                    "Order must contain at least one item"
            );
        }

        var productIds = new HashSet<UUID>();

        for (OrderItem item : items) {
            if (item == null) {
                throw new InvalidOrderException(
                        "Order items cannot be null"
                );
            }

            if (!productIds.add(item.productId())) {
                throw new InvalidOrderException(
                        "Order cannot contain duplicate products"
                );
            }
        }

        this.id = id;
        this.items = List.copyOf(items);
        this.createdAt = createdAt;
        this.status = status;
    }

    public static Order restore(
            UUID id,
            List<OrderItem> items,
            Instant createdAt,
            OrderStatus status
    ) {
        return new Order(id, items, createdAt, status);
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(OrderItem::subTotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    public void markStockReserved(){
        if (status == OrderStatus.PENDING_PAYMENT) {
            return;
        }

        requireStatus(OrderStatus.PENDING_STOCK);
        this.status = OrderStatus.PENDING_PAYMENT;
    }


    public boolean markPaymentApproved() {
        if (status == OrderStatus.PENDING_STOCK_CONFIRMATION
                || status == OrderStatus.CONFIRMED) {
            return false;
        }

        requireStatus(OrderStatus.PENDING_PAYMENT);
        status = OrderStatus.PENDING_STOCK_CONFIRMATION;
        return true;
    }

    public boolean markPaymentDeclined() {
        if (status == OrderStatus.PENDING_STOCK_RELEASE) {
            return false;
        }

        requireStatus(OrderStatus.PENDING_PAYMENT);
        status = OrderStatus.PENDING_STOCK_RELEASE;
        return true;
    }

    public boolean confirmStockWithdrawal() {
        if (status == OrderStatus.CONFIRMED) {
            return false;
        }

        requireStatus(OrderStatus.PENDING_STOCK_CONFIRMATION);
        status = OrderStatus.CONFIRMED;
        return true;
    }

    public boolean completeStockRelease() {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }

        requireStatus(OrderStatus.PENDING_STOCK_RELEASE);
        status = OrderStatus.CANCELLED;
        return true;
    }

    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            return;
        }

        if (status == OrderStatus.CONFIRMED) {
            throw  new InvalidOrderException(
                    "Confirmed order cannot be cancelled through this operation"
            );
        }

        requireStatus(OrderStatus.PENDING_STOCK);
        this.status = OrderStatus.CANCELLED;
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new InvalidOrderException(
                    "Expected order status %s but found %s"
                            .formatted(expected, status)
            );
        }
    }
}
