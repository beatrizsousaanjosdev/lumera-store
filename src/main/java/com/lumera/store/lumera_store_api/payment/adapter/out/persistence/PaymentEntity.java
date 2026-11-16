package com.lumera.store.lumera_store_api.payment.adapter.out.persistence;

import com.lumera.store.lumera_store_api.payment.domain.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    public PaymentEntity(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            Instant createdAt,
            PaymentStatus status
    ) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = status;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = Objects.requireNonNull(status, "Payment status is required.");
    }
}
