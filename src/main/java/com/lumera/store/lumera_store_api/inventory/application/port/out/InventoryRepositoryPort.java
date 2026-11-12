package com.lumera.store.lumera_store_api.inventory.application.port.out;

import com.lumera.store.lumera_store_api.inventory.domain.Inventory;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepositoryPort {
    Inventory save(Inventory inventory);
    Optional<Inventory> findByProductId(UUID productId);
    Optional<Inventory> findByProductIdForUpdate(UUID productId);
    void initializeIfAbsent(UUID productId);
}
