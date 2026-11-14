package com.lumera.store.lumera_store_api.order.adapter.in.web;

import com.lumera.store.lumera_store_api.order.adapter.in.web.dto.CreateOrderRequestDto;
import com.lumera.store.lumera_store_api.order.adapter.in.web.dto.OrderResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ProblemDetail;

import java.util.UUID;

@Tag(name = "Orders", description = "Order creation and lookup")
@RequestMapping(
        value = "/orders",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@ApiResponse(
        responseCode = "500",
        description = "Unexpected internal error.",
        content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
        )
)
public interface OrderApi {

    @Operation(
            summary = "Create order",
            description = "Creates an order using current catalog prices and active products."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Order created.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrderResponseDto.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or order data.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "One of the requested products was not found.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "409",
            description = "One of the requested products is inactive.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderResponseDto> create(
            @Valid @RequestBody CreateOrderRequestDto request
    );

    @Operation(summary = "Find order by ID")
    @ApiResponse(
            responseCode = "200",
            description = "Order found.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrderResponseDto.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid order ID format.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Order not found.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )
    )
    @GetMapping("/{id}")
    ResponseEntity<OrderResponseDto> findById(
            @PathVariable("id") UUID id
    );
}