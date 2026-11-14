package com.lumera.store.lumera_store_api.order.adapter.in.web;

import com.lumera.store.lumera_store_api.order.adapter.in.web.dto.CreateOrderRequestDto;
import com.lumera.store.lumera_store_api.order.adapter.in.web.dto.OrderResponseDto;
import com.lumera.store.lumera_store_api.order.application.command.CreateOrderCommand;
import com.lumera.store.lumera_store_api.order.application.command.CreateOrderItemCommand;
import com.lumera.store.lumera_store_api.order.application.port.in.CreateOrderUsecase;
import com.lumera.store.lumera_store_api.order.application.port.in.FindOrderByIdUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final CreateOrderUsecase createOrderUsecase;
    private final FindOrderByIdUsecase findOrderByIdUsecase;

    @Override
    public ResponseEntity<OrderResponseDto> create(
            CreateOrderRequestDto request
    ) {
        var items = request.items().stream()
                .map(item -> new CreateOrderItemCommand(
                        item.productId(),
                        item.quantity()
                ))
                .toList();

        var order = createOrderUsecase.execute(
                new CreateOrderCommand(items)
        );

        var location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(OrderResponseDto.from(order));
    }

    @Override
    public ResponseEntity<OrderResponseDto> findById(UUID id) {
        var order = findOrderByIdUsecase.execute(id);

        return ResponseEntity.ok(OrderResponseDto.from(order));
    }
}