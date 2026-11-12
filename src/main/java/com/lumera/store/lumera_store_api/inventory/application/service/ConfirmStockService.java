package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.exception.InventoryNotFoundException;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ConfirmStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmStockService implements ConfirmStockUsecase {

    private final InventoryRepositoryPort inventoryRepositoryPort;

    @Override
    @Transactional
    public Inventory execute(StockMovementCommand command) {
        log.debug(
                "Confirming reserved stock: productId={}, amount={}",
                command.productId(),
                command.amount()
        );

        Inventory inventory = inventoryRepositoryPort.findByProductIdForUpdate(
                        command.productId()
                )
                .orElseThrow(
                        () -> new InventoryNotFoundException(
                                command.productId()
                        )
                );

        inventory.confirmReservation(command.amount());

        return inventoryRepositoryPort.save(inventory);
    }
}
