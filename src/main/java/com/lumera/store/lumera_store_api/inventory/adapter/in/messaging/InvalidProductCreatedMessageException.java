package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

public class InvalidProductCreatedMessageException extends com.lumera.store.lumera_store_api.shared.infrastructure.messaging.InvalidIntegrationMessageException {

    public InvalidProductCreatedMessageException(String message) {
        super(message);
    }

    public InvalidProductCreatedMessageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
