package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

public class InvalidProductCreatedMessageException extends RuntimeException {

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
