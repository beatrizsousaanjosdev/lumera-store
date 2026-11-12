package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence;

import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private final InventoryJpaRepository repository;


    @Override
    public Inventory save(Inventory inventory) {
        InventoryEntity inventoryEntity = new InventoryEntity(
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity()
        );
        InventoryEntity saved = repository.save(inventoryEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<Inventory> findByProductId(UUID productId) {
        return repository.findById(productId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Inventory> findByProductIdForUpdate(UUID productId) {
        return repository.findByProductIdForUpdate(productId)
                .map(this::toDomain);
    }

    @Override
    public void initializeIfAbsent(UUID productId) {
        repository.initializeIfAbsent(productId);
    }

    private Inventory toDomain(InventoryEntity entity) {
        return Inventory.restore(
                entity.getProductId(),
                entity.getQuantity(),
                entity.getReservedQuantity()
        );
    }
}
