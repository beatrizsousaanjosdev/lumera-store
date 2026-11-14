package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.reservation;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservationEntity {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockReservationStatus status;

    @Column(
            name = "rejection_reason",
            columnDefinition = "TEXT",
            updatable = false
    )
    private String rejectionReason;

    @OneToMany(
            mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("position ASC")
    private List<StockReservationItemEntity> items = new ArrayList<>();

    public StockReservationEntity(
            UUID orderId,
            Instant createdAt,
            StockReservationStatus status,
            String rejectionReason
    ) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.status = status;
        this.rejectionReason = rejectionReason;
    }

    public void addItem(StockReservationItemEntity item) {
        item.attachTo(this);
        items.add(item);
    }

    public void updateStatus(StockReservationStatus status) {
        this.status = Objects.requireNonNull(
                status,
                "Reservation status is required"
        );
    }

    public List<StockReservationItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }



}
