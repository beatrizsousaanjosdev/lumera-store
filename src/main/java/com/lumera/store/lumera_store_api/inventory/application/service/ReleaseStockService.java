package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.exception.InventoryNotFoundException;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReleaseStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseStockService implements ReleaseStockUsecase {

    private final InventoryRepositoryPort port;

    @Override
    @Transactional
    public Inventory execute(StockMovementCommand command) {
        log.debug("Releasing reserved stock: productId={}, amount={}", command.productId(), command.amount());

        Inventory inventory = port.findByProductIdForUpdate(command.productId())
                .orElseThrow(() -> new InventoryNotFoundException(command.productId()));

        inventory.release(command.amount());

        return port.save(inventory);
    }
}
