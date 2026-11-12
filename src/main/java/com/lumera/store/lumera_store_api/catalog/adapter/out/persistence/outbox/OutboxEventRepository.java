package com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
              AND event_type = :eventType
            ORDER BY occurred_at, event_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findPendingForUpdate(
            @Param("eventType") String eventType,
            @Param("batchSize") int batchSize
    );
}
