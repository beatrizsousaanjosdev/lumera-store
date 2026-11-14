package com.lumera.store.lumera_store_api.order.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        OrderStatus status,
        Instant createdAt,
        BigDecimal total,
        List<Item> items
) {
    public  static OrderResponseDto from(Order order) {
        var items = order.getItems().stream()
                .map(item -> new Item(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice(),
                        item.subTotal()
                ))
                .toList();

        return new OrderResponseDto(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getTotal(),
                items
        );
    }

    @Schema(name = "OrderItemResponse")
    public record Item(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subTotal
    ) {}

}
