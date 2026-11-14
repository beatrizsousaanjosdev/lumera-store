package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.command.ReserveOrderStockCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReserveOrderStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservation;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationItem;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import com.lumera.store.lumera_store_api.inventory.application.event.StockReservationResultEvent;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationEventOutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReserveOrderStockService implements ReserveOrderStockUsecase {

    private final InventoryRepositoryPort inventoryPort;
    private final StockReservationRepositoryPort reservationPort;
    private final StockReservationEventOutboxPort eventOutboxPort;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockReservation execute(ReserveOrderStockCommand command) {
        reservationPort.lockByOrderId(command.orderId());

        var existing = reservationPort.findByOrderId(command.orderId());

        if (existing.isPresent()) {
            var reservation = existing.get();

            if (!new HashSet<>(reservation.getItems())
                    .equals(new HashSet<>(command.items()))) {
                throw new InvalidStockReservationException(
                        "Order already has a reservation with different items"
                );
            }

            log.debug(
                    "Returning existing reservation: orderId={}, status={}",
                    command.orderId(),
                    reservation.getStatus()
            );

            return reservation;
        }

        var sortedItems = command.items().stream()
                .sorted(Comparator.comparing(
                        item -> item.productId().toString()
                ))
                .toList();

        Map<UUID, Inventory> inventories = new LinkedHashMap<>();

        for (StockReservationItem item : sortedItems) {
            var inventory = inventoryPort.findByProductIdForUpdate(
                    item.productId()
            );

            if (inventory.isEmpty()) {
                return reject(
                        command,
                        "Inventory not initialized for product: "
                                + item.productId()
                );
            }

            if (inventory.get().getAvailableQuantity() < item.quantity()) {
                return reject(
                        command,
                        "Insufficient stock for product: "
                                + item.productId()
                );
            }

            inventories.put(item.productId(), inventory.get());
        }

        // Only start changing quantities after all items have passed validation.
        for (StockReservationItem item : sortedItems) {
            Inventory inventory = inventories.get(item.productId());

            inventory.reserve(item.quantity());
            inventoryPort.save(inventory);
        }

        var reservation = StockReservation.reserved(
                command.orderId(),
                command.items()
        );

        return saveDecision(reservation);
    }

    private StockReservation reject(
            ReserveOrderStockCommand command,
            String reason
    ) {
        log.debug(
                "Stock reservation rejected: orderId={}, reason={}",
                command.orderId(),
                reason
        );

        return saveDecision(
                StockReservation.rejected(
                        command.orderId(),
                        command.items(),
                        reason
                )
        );
    }
    private StockReservation saveDecision(StockReservation reservation) {
        var saved = reservationPort.save(reservation);
        eventOutboxPort.append(StockReservationResultEvent.create(saved));
        return saved;
    }
}