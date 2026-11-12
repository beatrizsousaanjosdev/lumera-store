package com.lumera.store.lumera_store_api.inventory.adapter.in.web.exception;

import com.lumera.store.lumera_store_api.inventory.application.exception.InventoryNotFoundException;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InsufficientStockException;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidInventoryException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@Slf4j
@RestControllerAdvice(basePackages =
        "com.lumera.store.lumera_store_api.inventory.adapter.in.web")
public class InventoryExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidInventoryException.class)
    public ProblemDetail handleInvalidInventory(
            InvalidInventoryException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid inventory", exception.getMessage(), request);
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ProblemDetail handleInventoryNotFound(
            InventoryNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Inventory not found", exception.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(
            InsufficientStockException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Insufficient stock", exception.getMessage(), request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrentModification(
            ConcurrencyFailureException exception, HttpServletRequest request) {
        log.warn("Inventory concurrency conflict: method={}, path={}",
                request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.CONFLICT, "Inventory concurrency conflict",
                "The inventory operation conflicted with another transaction. Retry the request.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        // Only this specific foreign key means the referenced product does not exist.
        // Other integrity violations may indicate a programming or schema error.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "fk_inventories_product".equals(violation.getConstraintName())
                    && "23503".equals(violation.getSQLState())) {
                return problem(HttpStatus.NOT_FOUND, "Product not found",
                        "Cannot initialize inventory because the product does not exist.", request);
            }
        }
        return handleUnexpected(exception, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, "One or more fields are invalid.");
        problem.setTitle("Validation error");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        // Includes InventoryInitializationException: an internal failure, not a missing resource.
        log.error("Unexpected inventory error: method={}, path={}",
                request.getMethod(), request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "The operation could not be completed.", request);
    }

    private ProblemDetail problem(
            HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    public record FieldViolation(String field, String message) {
    }
}
