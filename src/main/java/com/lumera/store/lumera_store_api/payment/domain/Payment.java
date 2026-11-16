package com.lumera.store.lumera_store_api.payment.domain;

import com.lumera.store.lumera_store_api.payment.domain.exception.InvalidPaymentException;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
public class Payment {

    private final UUID id;
    private final UUID orderId;
    private final BigDecimal amount;
    private final Instant createdAt;
    private PaymentStatus status;

    public Payment(UUID orderId, BigDecimal amount) {
        this(
                UUID.randomUUID(),
                orderId,
                amount,
                Instant.now(),
                PaymentStatus.PENDING
        );
    }

    private Payment(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            Instant createdAt,
            PaymentStatus status) {

        if (id == null || orderId == null) {
            throw new InvalidPaymentException(
                    "Payment ID and order ID are required"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidPaymentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (createdAt == null || status == null) {
            throw new InvalidPaymentException(
                    "Creation timestamp and status are required"
            );
        }

        this.id = id;
        this.orderId = orderId;
        this.amount = normalizeAmount(amount);
        this.createdAt = createdAt;
        this.status = status;

    }

    public static Payment restore(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            Instant createdAt,
            PaymentStatus status
    ) {
        return new Payment(
                id,
                orderId,
                amount,
                createdAt,
                status
        );
    }

    public boolean approve() {
        if (status == PaymentStatus.APPROVED) return false;

        requirePending();
        status = PaymentStatus.APPROVED;
        return true;

    }

    public boolean decline() {
        if (status == PaymentStatus.DECLINED) return false;

        requirePending();
        status = PaymentStatus.DECLINED;
        return true;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) throw new InvalidPaymentException("Only pending payments can change their outcome.");
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw  new InvalidPaymentException("Payment amount must have at most two decimal places.");
        }
    }


}
