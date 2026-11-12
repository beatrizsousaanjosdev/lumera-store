package com.lumera.store.lumera_store_api.catalog.application.exception;

public class InvalidPaginationException extends RuntimeException {
    public InvalidPaginationException() {
        super("Page must be non-negative and size must be between 1 and 100.");
    }
}
