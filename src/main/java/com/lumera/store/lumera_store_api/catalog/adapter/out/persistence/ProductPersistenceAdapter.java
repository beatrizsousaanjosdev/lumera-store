package com.lumera.store.lumera_store_api.catalog.adapter.out.persistence;

import com.lumera.store.lumera_store_api.catalog.application.model.ProductPage;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductRepositoryPort;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductRepository productRepository;


    @Override
    public Product save(Product product) {
        ProductEntity productEntity = new ProductEntity(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.isActive()
        );

        ProductEntity result = productRepository.save(productEntity);

        return Product.restore(
                result.getId(),
                result.getName(),
                result.getDescription(),
                result.getPrice(),
                result.getCategory(),
                result.isActive()
        );
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id)
                .map(entity -> Product.restore(
                        entity.getId(),
                        entity.getName(),
                        entity.getDescription(),
                        entity.getPrice(),
                        entity.getCategory(),
                        entity.isActive()));
    }

    @Override
    public ProductPage findAll(int page, int size, Category category, Boolean active) {
        var pageable = PageRequest.of(page, size,
                Sort.by("name").ascending()
                        .and(Sort.by("id").ascending()));

        Page<ProductEntity> result;
        if (category != null && active != null) {
            result = productRepository.findByCategoryAndActive(category, active, pageable);
        } else if (category != null) {
            result = productRepository.findByCategory(category, pageable);
        } else if (active != null) {
            result = productRepository.findByActive(active, pageable);
        } else {
            result = productRepository.findAll(pageable);
        }

        var products = result.getContent().stream()
                .map(entity -> Product.restore(
                        entity.getId(),
                        entity.getName(),
                        entity.getDescription(),
                        entity.getPrice(),
                        entity.getCategory(),
                        entity.isActive()))
                .toList();

        return new ProductPage(
                products,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

}
