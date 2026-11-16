package com.lumera.store.lumera_store_api.payment.application.service;

import com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand;
import com.lumera.store.lumera_store_api.payment.application.exception.ConflictingPaymentException;
import com.lumera.store.lumera_store_api.payment.application.port.in.ProcessPaymentUsecase;
import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentGatewayPort;
import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentRepositoryPort;
import com.lumera.store.lumera_store_api.payment.application.event.PaymentProcessedEvent;
import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentEventOutboxPort;
import com.lumera.store.lumera_store_api.payment.domain.Payment;
import com.lumera.store.lumera_store_api.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;



@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentService implements ProcessPaymentUsecase {

    private final PaymentRepositoryPort repository;
    private final PaymentGatewayPort gateway;
    private final PaymentEventOutboxPort eventOutbox;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment execute(ProcessPaymentCommand command) {
        repository.lockByOrderId(command.orderId());

        var existing = repository.findByOrderId(command.orderId());

        if (existing.isPresent()
                && existing.get().getAmount().compareTo(command.amount()) != 0) {
            throw new ConflictingPaymentException(command.orderId());
        }

        var payment = existing.orElseGet(
                () -> new Payment(command.orderId(), command.amount())
        );

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.debug(
                    "Returning recorded payment: orderId={}, status={}",
                    payment.getOrderId(),
                    payment.getStatus()
            );

            return payment;
        }

        var decision = Objects.requireNonNull(
                gateway.charge(
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getAmount()
                ),
                "Payment gateway must return a decision"
        );

        switch (decision) {
            case APPROVED -> payment.approve();
            case DECLINED -> payment.decline();
        }

        var saved = repository.save(payment);

        eventOutbox.append(PaymentProcessedEvent.create(saved));

        log.debug(
                "Payment result staged: paymentId={}, orderId={}, status={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getStatus()
        );

        return saved;
    }
}