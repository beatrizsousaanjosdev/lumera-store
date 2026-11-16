package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.ApplyPaymentResultCommand;
import com.lumera.store.lumera_store_api.order.application.event.StockFinalizationRequestedEvent;
import com.lumera.store.lumera_store_api.order.application.exception.ConflictingPaymentResultException;
import com.lumera.store.lumera_store_api.order.application.exception.OrderNotFoundException;
import com.lumera.store.lumera_store_api.order.application.port.in.ApplyPaymentResultUsecase;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderEventOutboxPort;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderPaymentResultPort;
import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplyPaymentResultService implements ApplyPaymentResultUsecase {

    private final OrderRepositoryPort repository;
    private final OrderEventOutboxPort eventOutbox;
    private final OrderPaymentResultPort results;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order execute(ApplyPaymentResultCommand command) {
        var order = repository.findByIdForUpdate(command.orderId())
                .orElseThrow(
                        () -> new OrderNotFoundException(command.orderId())
                );

        if (order.getTotal().compareTo(command.amount()) != 0) {
            throw new ConflictingPaymentResultException(order.getId());
        }

        var existing = results.findByOrderId(order.getId());
        if (existing.isPresent()) {
            var recorded = existing.get();
            if (!recorded.paymentId().equals(command.paymentId())
                    || recorded.amount().compareTo(command.amount()) != 0
                    || recorded.approved() != command.approved()) {
                throw new ConflictingPaymentResultException(order.getId());
            }
            return order;
        }

        var status = order.getStatus();
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictingPaymentResultException(order.getId());
        }

        if (command.approved()) {
            order.markPaymentApproved();
        } else {
            order.markPaymentDeclined();
        }

        results.save(command);
        var saved = repository.save(order);

        eventOutbox.appendStockFinalizationRequested(
                StockFinalizationRequestedEvent.create(saved)
        );

        return saved;
    }
}