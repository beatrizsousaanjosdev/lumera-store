package com.lumera.store.lumera_store_api.order.domain;

public enum OrderStatus {
    PENDING_STOCK,
    PENDING_PAYMENT,
    PENDING_STOCK_CONFIRMATION,
    PENDING_STOCK_RELEASE,
    CONFIRMED,
    CANCELLED
}
