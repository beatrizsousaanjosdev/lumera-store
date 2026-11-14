package com.lumera.store.lumera_store_api.shared.infrastructure.outbox.messaging;

import java.util.UUID;

public class OutboxPublicationException extends RuntimeException {

    public OutboxPublicationException(UUID eventId, Throwable cause) {
        super("Outbox publication failed: " + eventId, cause);
    }
}
