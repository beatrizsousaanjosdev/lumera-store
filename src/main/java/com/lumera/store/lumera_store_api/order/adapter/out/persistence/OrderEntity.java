package com.lumera.store.lumera_store_api.order.adapter.out.persistence;

import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Id
    @Getter
    private UUID id;

    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("position ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    public OrderEntity(
            UUID id,
            Instant createdAt,
            OrderStatus status
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.status = status;
    }

    public void addItem(OrderItemEntity item) {
        item.attachTo(this);
        items.add(item);
    }

    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void updateStatus(OrderStatus status) {
        this.status = java.util.Objects.requireNonNull(
                status,
                "Order status is required"
        );
    }

}
