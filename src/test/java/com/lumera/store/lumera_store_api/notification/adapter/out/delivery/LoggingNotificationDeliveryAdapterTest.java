package com.lumera.store.lumera_store_api.notification.adapter.out.delivery;

import com.lumera.store.lumera_store_api.notification.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LoggingNotificationDeliveryAdapterTest {
    @Test void shouldDeferLogUntilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            var adapter = new LoggingNotificationDeliveryAdapter();
            adapter.deliver(new OrderNotification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NotificationOutcome.CONFIRMED, Instant.now()));
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            assertDoesNotThrow(() -> callbacks.getFirst().afterCommit());
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
