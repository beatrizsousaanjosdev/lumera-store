package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.reservation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "stock_reservation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StockReservationEntity reservation;

    @Column(name = "item_position", nullable = false)
    private int position;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    public StockReservationItemEntity(
            int position,
            UUID productId,
            int quantity
    ) {
        this.position = position;
        this.productId = productId;
        this.quantity = quantity;
    }

    void attachTo(StockReservationEntity reservation) {
        this.reservation = reservation;
    }
}