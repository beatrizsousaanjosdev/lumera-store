package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.reservation;

import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationStatus;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationItem;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StockReservationPersistenceAdapter
        implements StockReservationRepositoryPort {

    private final StockReservationJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockByOrderId(UUID orderId) {
        if (orderId == null) {
            throw new InvalidStockReservationException(
                    "Order ID is required"
            );
        }

        String lockKey = "stock-reservation:" + orderId;

        jdbcTemplate.execute(
                """
                SELECT pg_advisory_xact_lock(
                    hashtextextended(?, 0)
                )
                """,
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setString(1, lockKey);
                    statement.execute();
                    return null;
                }
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockReservation> findByOrderId(UUID orderId) {
        return repository.findById(orderId)
                .map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StockReservation save(StockReservation reservation) {
        var existing = repository.findById(reservation.getOrderId());

        if (existing.isPresent()) {
            var entity = existing.get();
            entity.updateStatus(reservation.getStatus());

            return toDomain(entity);
        }

        var entity = toEntity(reservation);
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveReservationForProduct(UUID productId) {
        return repository.existsByStatusAndItems_ProductId(StockReservationStatus.RESERVED, productId);
    }

    private StockReservationEntity toEntity(
            StockReservation reservation
    ) {
        var entity = new StockReservationEntity(
                reservation.getOrderId(),
                reservation.getCreatedAt(),
                reservation.getStatus(),
                reservation.getRejectionReason()
        );

        for (int position = 0;
             position < reservation.getItems().size();
             position++) {

            var item = reservation.getItems().get(position);

            entity.addItem(new StockReservationItemEntity(
                    position,
                    item.productId(),
                    item.quantity()
            ));
        }

        return entity;
    }

    private StockReservation toDomain(
            StockReservationEntity entity
    ) {
        var items = entity.getItems().stream()
                .map(item -> new StockReservationItem(
                        item.getProductId(),
                        item.getQuantity()
                ))
                .toList();

        return StockReservation.restore(
                entity.getOrderId(),
                items,
                entity.getCreatedAt(),
                entity.getStatus(),
                entity.getRejectionReason()
        );
    }
}