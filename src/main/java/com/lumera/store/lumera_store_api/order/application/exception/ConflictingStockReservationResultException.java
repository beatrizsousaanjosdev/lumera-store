package com.lumera.store.lumera_store_api.order.application.exception;

import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import java.util.UUID;

public class ConflictingStockReservationResultException extends RuntimeException {
    public ConflictingStockReservationResultException(UUID id, OrderStatus status, boolean reserved) {
        super("Reservation result conflicts with order state: orderId=" + id + ", status=" + status + ", reserved=" + reserved);
    }
}
