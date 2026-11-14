package com.lumera.store.lumera_store_api.order.adapter.in.web.exception;

import com.lumera.store.lumera_store_api.order.application.exception.InactiveProductException;
import com.lumera.store.lumera_store_api.order.application.exception.OrderNotFoundException;
import com.lumera.store.lumera_store_api.order.application.exception.OrderProductNotFoundException;
import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@Slf4j
@RestControllerAdvice(basePackages =
        "com.lumera.store.lumera_store_api.order.adapter.in.web")
public class OrderExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    public ProblemDetail handleInvalidOrder(
            InvalidOrderException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid order",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            OrderNotFoundException.class,
            OrderProductNotFoundException.class
    })
    public ProblemDetail handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InactiveProductException.class)
    public ProblemDetail handleInactiveProduct(
            InactiveProductException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Inactive product",
                exception.getMessage(),
                request
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                status,
                "One or more fields are invalid."
        );
        problem.setTitle("Validation error");

        var errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        problem.setProperty("errors", errors);

        return handleExceptionInternal(
                exception, problem, headers, status, request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected order error: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "The operation could not be completed.",
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    public record FieldViolation(String field, String message) {
    }
}