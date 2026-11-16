package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.CompleteOrderStockCommand;
import com.lumera.store.lumera_store_api.order.application.exception.ConflictingStockFinalizationException;
import com.lumera.store.lumera_store_api.order.application.exception.OrderNotFoundException;
import com.lumera.store.lumera_store_api.order.application.port.in.CompleteOrderStockUsecase;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.application.event.OrderCompletedEvent;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderEventOutboxPort;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteOrderStockService implements CompleteOrderStockUsecase {

    private final OrderRepositoryPort repository;
    private final OrderEventOutboxPort eventOutbox;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order execute(CompleteOrderStockCommand command) {
        var order = repository.findByIdForUpdate(command.orderId())
                .orElseThrow(
                        () -> new OrderNotFoundException(command.orderId())
                );

        if (command.confirmed()) {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                return order;
            }

            if (order.getStatus() != OrderStatus.PENDING_STOCK_CONFIRMATION) {
                throw new ConflictingStockFinalizationException(order.getId());
            }

            order.confirmStockWithdrawal();
        } else {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return order;
            }

            if (order.getStatus() != OrderStatus.PENDING_STOCK_RELEASE) {
                throw new ConflictingStockFinalizationException(order.getId());
            }

            order.completeStockRelease();
        }

        var saved = repository.save(order);
        eventOutbox.appendCompleted(OrderCompletedEvent.create(saved));

        log.debug(
                "Order completion staged: orderId={}, status={}",
                saved.getId(),
                saved.getStatus()
        );

        return saved;
    }
}