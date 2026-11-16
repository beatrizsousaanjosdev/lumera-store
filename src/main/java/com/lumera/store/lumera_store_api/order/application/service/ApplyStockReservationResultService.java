package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.StockReservationResultCommand;
import com.lumera.store.lumera_store_api.order.application.exception.ConflictingStockReservationResultException;
import com.lumera.store.lumera_store_api.order.application.exception.OrderNotFoundException;
import com.lumera.store.lumera_store_api.order.application.port.in.ApplyStockReservationResultUsecase;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderPaymentResultPort;
import com.lumera.store.lumera_store_api.order.application.event.PaymentRequestedEvent;
import com.lumera.store.lumera_store_api.order.application.event.OrderCompletedEvent;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderEventOutboxPort;
import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplyStockReservationResultService implements ApplyStockReservationResultUsecase {
    private final OrderRepositoryPort port;
    private final OrderEventOutboxPort eventOutbox;
    private final OrderPaymentResultPort paymentResults;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order execute(StockReservationResultCommand command) {
        var order = port.findByIdForUpdate(command.orderId())
                .orElseThrow(
                        () -> new OrderNotFoundException(command.orderId())
                );

        if (command.reserved()) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                    || order.getStatus() == OrderStatus.PENDING_STOCK_CONFIRMATION
                    || order.getStatus() == OrderStatus.PENDING_STOCK_RELEASE
                    || order.getStatus() == OrderStatus.CONFIRMED
                    || order.getStatus() == OrderStatus.CANCELLED
                        && paymentResults.findByOrderId(order.getId()).isPresent()) {
                return order;
            }

            if (order.getStatus() != OrderStatus.PENDING_STOCK) {
                throw conflict(command, order);
            }

            order.markStockReserved();

            var saved = port.save(order);

            eventOutbox.appendPaymentRequested(
                    PaymentRequestedEvent.create(saved)
            );

            return saved;
        }

        if (order.getStatus() == OrderStatus.CANCELLED
                && paymentResults.findByOrderId(order.getId()).isEmpty()) {
            return order;
        }

        if (order.getStatus() != OrderStatus.PENDING_STOCK) {
            throw conflict(command, order);
        }

        order.cancel();
        var saved = port.save(order);
        eventOutbox.appendCompleted(OrderCompletedEvent.create(saved));
        return saved;
    }

    private ConflictingStockReservationResultException conflict(StockReservationResultCommand command, Order order) {
        return new ConflictingStockReservationResultException(command.orderId(), order.getStatus(), command.reserved());
    }
}
