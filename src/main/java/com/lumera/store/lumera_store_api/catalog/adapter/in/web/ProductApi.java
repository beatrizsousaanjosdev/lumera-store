package com.lumera.store.lumera_store_api.catalog.adapter.in.web;

import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.CreateProductRequestDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.ProductPageResponseDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.ProductResponseDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.UpdateProductActiveRequestDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.UpdateProductRequesDto;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Products", description = "Product operations catalog")
@RequestMapping("/products")
public interface ProductApi {

    @Operation(
            summary = "Register product",
            description = "Register a product with a generated identifier and active status."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Product successfully registered.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProductResponseDto.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request body.",
            content = @Content
    )
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces =  MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<ProductResponseDto> create(@Valid @RequestBody CreateProductRequestDto createProductRequestDto);

    @Operation(
            summary = "Find product by ID",
            description = "Returns the product associated with the provided ID."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Product found.",
            content =  @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema =  @Schema(implementation = ProductResponseDto.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Product not found.",
            content =  @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema =  @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid product ID.",
            content =  @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema =  @Schema(implementation = ProblemDetail.class)
            )
    )
    @GetMapping(
            value = "/{id}",
            produces =  MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<ProductResponseDto> findById(@PathVariable("id") UUID id);

    @Operation(
            summary = "Update product.",
            description = "Updates name, description and price, preserving ID and active status."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Product updated.",
            content =  @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema =  @Schema(implementation = ProductResponseDto.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid ID or product data.",
            content =  @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema =  @Schema(implementation = ProblemDetail.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Product not found.",
            content =  @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema =  @Schema(implementation = ProblemDetail.class)
            )
    )
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<ProductResponseDto> update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateProductRequesDto request);

    @Operation(summary = "List products", description = "Lists products ordered by name and ID. Pages start at zero; size ranges from 1 to 100.")
    @ApiResponse(responseCode = "200", description = "Products listed.")
    @ApiResponse(responseCode = "400", description = "Invalid pagination or category.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductPageResponseDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Boolean active);


    @Operation(summary = "Update product active status", description = "Activates or deactivates a product. Repeating the current status succeeds.")
    @ApiResponse(responseCode = "200", description = "Product active status updated.")
    @ApiResponse(responseCode = "400", description = "Invalid ID or missing active status.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Product not found.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping(value = "/{id}/active", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductResponseDto> updateActive(@PathVariable("id") UUID id,
            @Valid @RequestBody UpdateProductActiveRequestDto request);

}
