package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryJpaRepository extends JpaRepository<InventoryEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT inventory
        FROM InventoryEntity inventory
        WHERE inventory.productId = :productId
    """)
    Optional<InventoryEntity> findByProductIdForUpdate(
            @Param("productId") UUID productId
    );

    @Modifying
    @Query(value = """
        INSERT INTO inventories (
            product_id,
            quantity,
            reserved_quantity
        )
        VALUES (:productId, 0, 0)
        ON CONFLICT (product_id) DO NOTHING
    """, nativeQuery = true)
    int initializeIfAbsent(@Param("productId") UUID productId);
}
