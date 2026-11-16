package com.lumera.store.lumera_store_api.notification.adapter.out.persistence;

import com.lumera.store.lumera_store_api.notification.application.port.out.NotificationRepositoryPort;
import com.lumera.store.lumera_store_api.notification.domain.NotificationOutcome;
import com.lumera.store.lumera_store_api.notification.domain.OrderNotification;
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
public class NotificationPersistenceAdapter implements NotificationRepositoryPort {
    private final JdbcTemplate jdbc;

    @Override
    public void lockByOrderId(UUID orderId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { return null; }, "notification:" + orderId);
    }

    @Override
    public Optional<OrderNotification> findByOrderId(UUID orderId) {
        return jdbc.query("SELECT * FROM order_notifications WHERE order_id = ?", (rs, row) ->
                new OrderNotification(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getObject("order_id", UUID.class), NotificationOutcome.valueOf(rs.getString("outcome")),
                        rs.getTimestamp("processed_at").toInstant()), orderId).stream().findFirst();
    }

    @Override
    public void save(OrderNotification notification) {
        jdbc.update("INSERT INTO order_notifications(id, event_id, order_id, outcome, processed_at) VALUES (?, ?, ?, ?, ?)",
                notification.id(), notification.eventId(), notification.orderId(), notification.outcome().name(),
                java.sql.Timestamp.from(notification.processedAt()));
    }
}
