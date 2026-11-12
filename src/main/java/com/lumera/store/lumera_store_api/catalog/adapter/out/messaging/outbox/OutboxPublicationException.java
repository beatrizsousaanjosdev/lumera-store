package com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox;

import java.util.UUID;

public class OutboxPublicationException extends RuntimeException {

    public OutboxPublicationException(UUID eventId, Throwable cause) {
        super("Outbox publication failed: " + eventId, cause);
    }
}
