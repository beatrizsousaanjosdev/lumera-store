package com.lumera.store.lumera_store_api.notification.domain;

import com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrderNotificationTest {
    @Test void shouldRequireCompleteIdentityAndOutcome() {
        UUID id = UUID.randomUUID();var now = Instant.now();var outcome = NotificationOutcome.CONFIRMED;
        assertThrows(NullPointerException.class, () -> new OrderNotification(null,id,id,outcome,now));
        assertThrows(NullPointerException.class, () -> new OrderNotification(id,null,id,outcome,now));
        assertThrows(NullPointerException.class, () -> new OrderNotification(id,id,null,outcome,now));
        assertThrows(NullPointerException.class, () -> new OrderNotification(id,id,id,null,now));
        assertThrows(NullPointerException.class, () -> new OrderNotification(id,id,id,outcome,null));
        assertThrows(NullPointerException.class, () -> new NotifyOrderOutcomeCommand(null,id,outcome));
        assertThrows(NullPointerException.class, () -> new NotifyOrderOutcomeCommand(id,null,outcome));
        assertThrows(NullPointerException.class, () -> new NotifyOrderOutcomeCommand(id,id,null));
    }
}
