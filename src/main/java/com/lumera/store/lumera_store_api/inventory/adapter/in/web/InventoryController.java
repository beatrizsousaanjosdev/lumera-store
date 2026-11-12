package com.lumera.store.lumera_store_api.inventory.adapter.in.web;

import com.lumera.store.lumera_store_api.inventory.adapter.in.web.dto.InventoryResponseDto;
import com.lumera.store.lumera_store_api.inventory.adapter.in.web.dto.StockMovementRequestDto;
import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.*;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InventoryController implements InventoryApi {

    private final InitializeInventoryUsecase initializeInventoryUsecase;
    private final FindInventoryByProductIdUsecase findInventoryByProductIdUsecase;
    private final AddStockUsecase addStockUsecase;
    private final ReserveStockUsecase reserveStockUsecase;
    private final ReleaseStockUsecase releaseStockUsecase;
    private final ConfirmStockUsecase confirmStockUsecase;

    @Override
    public ResponseEntity<InventoryResponseDto> initialize(UUID productId) {
        Inventory inventory = initializeInventoryUsecase.execute(productId);
        return ResponseEntity.ok(response(inventory));
    }

    @Override
    public ResponseEntity<InventoryResponseDto> findByProductId(UUID productId) {
        Inventory inventory = findInventoryByProductIdUsecase.execute(productId);
        return ResponseEntity.ok(response(inventory));
    }

    @Override
    public ResponseEntity<InventoryResponseDto> addStock(
            UUID productId,
            StockMovementRequestDto request
    ) {
        Inventory inventory = addStockUsecase.execute(
                new StockMovementCommand(productId, request.amount())
        );
        return ResponseEntity.ok(response(inventory));
    }

    @Override
    public ResponseEntity<InventoryResponseDto> reserve(
            UUID productId,
            StockMovementRequestDto request
    ) {
        Inventory inventory = reserveStockUsecase.execute(
                new StockMovementCommand(productId, request.amount())
        );
        return ResponseEntity.ok(response(inventory));
    }

    @Override
    public ResponseEntity<InventoryResponseDto> release(
            UUID productId,
            StockMovementRequestDto request
    ) {
        Inventory inventory = releaseStockUsecase.execute(
                new StockMovementCommand(productId, request.amount())
        );
        return ResponseEntity.ok(response(inventory));
    }

    @Override
    public ResponseEntity<InventoryResponseDto> confirm(
            UUID productId,
            StockMovementRequestDto request
    ) {
        Inventory inventory = confirmStockUsecase.execute(
                new StockMovementCommand(productId, request.amount())
        );
        return ResponseEntity.ok(response(inventory));
    }

    private InventoryResponseDto response(Inventory inventory) {
        return new InventoryResponseDto(
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity()
        );
    }
}