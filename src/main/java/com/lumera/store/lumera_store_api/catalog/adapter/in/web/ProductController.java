package com.lumera.store.lumera_store_api.catalog.adapter.in.web;

import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.CreateProductRequestDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.ProductPageResponseDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.ProductResponseDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.UpdateProductActiveRequestDto;
import com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto.UpdateProductRequesDto;
import com.lumera.store.lumera_store_api.catalog.application.port.in.CreateProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.FindProductByIdUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.ListProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.UpdateProductActiveUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.UpdateProductUsecase;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProductController implements ProductApi{

    private final UpdateProductActiveUsecase updateProductActiveUsecase;

    private final ListProductsUsecase listProductsUsecase;
    private final CreateProductsUsecase createProductsUsecase;
    private final FindProductByIdUsecase findProductByIdUsecase;
    private final UpdateProductUsecase updateProductUsecase;

    @Override
    public ResponseEntity<ProductResponseDto> create(CreateProductRequestDto createProductRequestDto) {
        Product product = createProductsUsecase.execute(
                createProductRequestDto.name(),
                createProductRequestDto.description(),
                createProductRequestDto.price(),
                createProductRequestDto.category()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response(product));
    }

    @Override
    public ResponseEntity<ProductResponseDto> findById(UUID id) {
        Product product = findProductByIdUsecase.execute(id);
        return ResponseEntity.ok(response(product));
    }

    @Override
    public ResponseEntity<ProductResponseDto> update(UUID id, UpdateProductRequesDto request) {
        Product product = updateProductUsecase.execute(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.category()
        );

        return ResponseEntity.ok(response(product));
    }

    private ProductResponseDto response(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.isActive()
        );
    }

    @Override
    public ResponseEntity<ProductPageResponseDto> list(
            int page, int size, Category category, Boolean active) {
        var result = listProductsUsecase.execute(page, size, category, active);
        return ResponseEntity.ok(new ProductPageResponseDto(
                result.content().stream().map(this::response).toList(), result.page(),
                result.size(), result.totalElements(), result.totalPages()));
    }

    @Override
    public ResponseEntity<ProductResponseDto> updateActive(
            UUID id, UpdateProductActiveRequestDto request) {
        return ResponseEntity.ok(response(updateProductActiveUsecase.execute(id, request.active())));
    }

}
