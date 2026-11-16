package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.exception.InventoryNotFoundException;
import com.lumera.store.lumera_store_api.inventory.application.exception.StockReservationNotFoundException;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReleaseOrderStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import com.lumera.store.lumera_store_api.inventory.application.event.StockFinalizedEvent;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationEventOutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseOrderStockService implements ReleaseOrderStockUsecase {

    private final InventoryRepositoryPort inventoryPort;
    private final StockReservationRepositoryPort reservationPort;
    private final StockReservationEventOutboxPort eventOutbox;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockReservation execute(UUID orderId) {
        if (orderId == null) throw new InvalidStockReservationException("Order id is required");

        reservationPort.lockByOrderId(orderId);

        var reservation = reservationPort.findByOrderId(orderId)
                .orElseThrow(() -> new StockReservationNotFoundException(orderId));

        if (!reservation.release()) {
            log.debug("Reservation already released: orderId={}", orderId);
            return reservation;
        }

        var sortedItems = reservation.getItems().stream()
                .sorted(Comparator.comparing(
                        item -> item.productId().toString()
                ))
                .toList();

        for (var item : sortedItems ) {
            var inventory = inventoryPort.findByProductIdForUpdate(item.productId())
                    .orElseThrow( () -> new InventoryNotFoundException(item.productId()));
            inventory.release(item.quantity());
            inventoryPort.save(inventory);
        }

        var saved = reservationPort.save(reservation);

        eventOutbox.appendFinalized(
                StockFinalizedEvent.create(saved)
        );

        log.debug("Stock release staged: orderId={}", orderId);

        return saved;
    }
}
