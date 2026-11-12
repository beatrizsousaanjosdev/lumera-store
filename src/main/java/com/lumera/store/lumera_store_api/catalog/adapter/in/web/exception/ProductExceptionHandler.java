package com.lumera.store.lumera_store_api.catalog.adapter.in.web.exception;

import com.lumera.store.lumera_store_api.catalog.application.exception.InvalidPaginationException;
import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.domain.exception.InvalidProductException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice(basePackages =
        "com.lumera.store.lumera_store_api.catalog.adapter.in.web")
public class ProductExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidProductException.class)
    public ProblemDetail handleInvalidProduct(
            InvalidProductException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid product",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(
            ProductNotFoundException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Product not found",
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
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                "One or more fields are invalids."
        );
        problemDetail.setTitle("Validation error");

        var errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();
        problemDetail.setProperty("errors", errors);

        return handleExceptionInternal(exception, problemDetail, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
          "Unexpected error: method={}, path={}",
          request.getMethod(),
          request.getRequestURI(),
          exception
        );

        return  problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "The operation could not be completed",
                request
        );
    }
    
    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return  problem;
    }

    public record FieldViolation(String field, String message) {}

    @ExceptionHandler(InvalidPaginationException.class)
    public ProblemDetail handleInvalidPagination(
            InvalidPaginationException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid pagination", exception.getMessage(), request);
    }

}
