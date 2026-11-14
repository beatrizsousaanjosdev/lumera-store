package com.lumera.store.lumera_store_api.order.adapter.out.persistence;


import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository
extends JpaRepository<OrderEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT purchaseOrder
            FROM OrderEntity purchaseOrder
            WHERE purchaseOrder.id = :id
    """)
  Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);
}
