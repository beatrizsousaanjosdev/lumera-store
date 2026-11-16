package com.lumera.store.lumera_store_api.order.adapter.out.persistence;

import com.lumera.store.lumera_store_api.order.application.command.ApplyPaymentResultCommand;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderPaymentResultPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderPaymentResultAdapter implements OrderPaymentResultPort {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<ApplyPaymentResultCommand> findByOrderId(UUID orderId) {
        return jdbc.query("SELECT order_id, payment_id, amount, approved FROM order_payment_results WHERE order_id = ?",
                (rs, row) -> new ApplyPaymentResultCommand(rs.getObject("order_id", UUID.class),
                        rs.getObject("payment_id", UUID.class), rs.getBigDecimal("amount"), rs.getBoolean("approved")),
                orderId).stream().findFirst();
    }

    @Override
    public void save(ApplyPaymentResultCommand result) {
        jdbc.update("INSERT INTO order_payment_results(order_id, payment_id, amount, approved) VALUES (?, ?, ?, ?)",
                result.orderId(), result.paymentId(), result.amount(), result.approved());
    }
}
