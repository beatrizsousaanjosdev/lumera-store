package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

public class InvalidIntegrationMessageException extends RuntimeException {
    public InvalidIntegrationMessageException(String message) { super(message); }
    public InvalidIntegrationMessageException(String message, Throwable cause) { super(message, cause); }
}
