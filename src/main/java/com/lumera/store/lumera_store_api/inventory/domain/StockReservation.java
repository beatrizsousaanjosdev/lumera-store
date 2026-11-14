package com.lumera.store.lumera_store_api.inventory.domain;

import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Getter
public class StockReservation {
    private final UUID orderId;
    private final List<StockReservationItem> items;
    private final Instant createdAt;
    private final String rejectionReason;
    private StockReservationStatus status;

    private StockReservation(
            UUID orderId,
            List<StockReservationItem> items,
            Instant createdAt,
            String rejectionReason,
            StockReservationStatus status
    ) {
        if (orderId == null || createdAt == null || status == null) {
            throw new InvalidStockReservationException(
                    "Order ID, creation timestamp and status are required"
            );
        }

        if (items == null || items.isEmpty()) {
            throw new InvalidStockReservationException(
                    "Reservation must contain at least one item"
            );
        }

        var productIds = new HashSet<UUID>();

        for (var item : items) {
            if (item == null) {
                throw new InvalidStockReservationException(
                        "Reservation items cannot be null"
                );
            }

            if (!productIds.add(item.productId())) {
                throw new InvalidStockReservationException(
                        "Reservation cannot contain duplicate products"
                );
            }
        }

        if (status == StockReservationStatus.REJECTED) {
            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new InvalidStockReservationException(
                        "Rejected reservation requires a reason"
                );
            }

            rejectionReason = rejectionReason.strip();
        } else if (rejectionReason != null) {
            throw new InvalidStockReservationException(
                    "Only rejected reservations can have a rejection reason"
            );
        }

        this.orderId = orderId;
        this.items = List.copyOf(items);
        this.createdAt = createdAt;
        this.status = status;
        this.rejectionReason = rejectionReason;
    }

    public static StockReservation reserved(
            UUID orderId,
            List<StockReservationItem> items
    ){
        return  new StockReservation(
                orderId,
                items,
                Instant.now(),
                null,
                StockReservationStatus.RESERVED
        );

    }

    public static StockReservation rejected(
            UUID orderId,
            List<StockReservationItem> items,
            String rejectionReason
    ) {

        return new StockReservation(
                orderId,
                items,
                Instant.now(),
                rejectionReason,
                StockReservationStatus.REJECTED
        );

    }

    public static StockReservation restore(
            UUID orderId,
            List<StockReservationItem> items,
            Instant createdAt,
            StockReservationStatus status,
            String rejectionReason
    ) {

        return new StockReservation(
                orderId,
                items,
                createdAt,
                rejectionReason,
                status
        );

    }

    public boolean release() {
        if (status == StockReservationStatus.RELEASED) {
            return false;
        }

        requireReserved();
        this.status = StockReservationStatus.RELEASED;
        return true;
    }

    public boolean confirm() {
        if (status == StockReservationStatus.CONFIRMED) {
            return false;
        }

        requireReserved();
        this.status = StockReservationStatus.CONFIRMED;
        return true;
    }

    private void requireReserved() {
        if (status != StockReservationStatus.RESERVED) {
            throw new InvalidStockReservationException(
                    "Reservation must be RESERVED but was " + status
            );
        }
    }
}
