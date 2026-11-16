package com.lumera.store.lumera_store_api.notification.application.command;

import com.lumera.store.lumera_store_api.notification.domain.NotificationOutcome;
import java.util.Objects;
import java.util.UUID;

public record NotifyOrderOutcomeCommand(UUID eventId, UUID orderId, NotificationOutcome outcome) {
    public NotifyOrderOutcomeCommand {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(outcome, "Order outcome is required");
    }
}
