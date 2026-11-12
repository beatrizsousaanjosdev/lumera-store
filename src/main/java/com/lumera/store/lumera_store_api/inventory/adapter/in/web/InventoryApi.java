package com.lumera.store.lumera_store_api.inventory.adapter.in.web;

import com.lumera.store.lumera_store_api.inventory.adapter.in.web.dto.InventoryResponseDto;
import com.lumera.store.lumera_store_api.inventory.adapter.in.web.dto.StockMovementRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


import java.util.UUID;

@Tag(name = "Inventory", description = "Product inventory operations")
@RequestMapping(
        value = "/inventories",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public interface InventoryApi {

    @Operation(
            summary = "Initialize inventory",
            description = "Creates zero inventory if absent; preserves existing quantities."
    )
    @PutMapping("/{productId}")
    ResponseEntity<InventoryResponseDto> initialize(

            @PathVariable("productId") UUID productId
    );

    @Operation(summary = "Find inventory by product ID")
    @GetMapping("/{productId}")
    ResponseEntity<InventoryResponseDto> findByProductId(
            @PathVariable("productId") UUID productId
    );

    @Operation(summary = "Add stock")
    @PostMapping(
            value = "/{productId}/entries",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<InventoryResponseDto> addStock(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody StockMovementRequestDto request
    );

    @Operation(summary = "Reserve stock")
    @PostMapping(
            value = "/{productId}/reservations",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<InventoryResponseDto> reserve(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody StockMovementRequestDto request
    );

    @Operation(summary = "Release reserved stock")
    @PostMapping(
            value = "/{productId}/reservations/release",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<InventoryResponseDto> release(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody StockMovementRequestDto request
    );

    @Operation(summary = "Confirm reserved stock withdrawal")
    @PostMapping(
            value = "/{productId}/reservations/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<InventoryResponseDto> confirm(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody StockMovementRequestDto request
    );
}
