package com.lumera.store.lumera_store_api.payment.adapter.out.persistence;

import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentRepositoryPort;
import com.lumera.store.lumera_store_api.payment.domain.Payment;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository repository;
    private final EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockByOrderId(UUID orderId) {
        entityManager.createNativeQuery("""
                SELECT pg_advisory_xact_lock(
                       hashtextextended(CAST(:lockKey AS text), 0)
                )
                """)
                .setParameter("lockKey", "payment:" + orderId)
                .getSingleResult();

    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId)
                .map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment save(Payment payment) {
        var existing = repository.findByOrderId(payment.getOrderId());

        if (existing.isPresent()) {
            var entity = existing.get();

            if (!entity.getId().equals(payment.getId())
                    || entity.getAmount().compareTo(payment.getAmount()) != 0) {
                throw new IllegalStateException(
                        "Cannot replace an existing payment"
                );
            }

            entity.updateStatus(payment.getStatus());
            return toDomain(entity);
        }

        var entity = new PaymentEntity(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCreatedAt(),
                payment.getStatus()
        );

        var result = repository.save(entity);

        return toDomain(result);
    }

    private Payment toDomain(PaymentEntity paymentEntity) {
        return Payment.restore(
                paymentEntity.getId(),
                paymentEntity.getOrderId(),
                paymentEntity.getAmount(),
                paymentEntity.getCreatedAt(),
                paymentEntity.getStatus()
        );
    }


}
