package com.lumera.store.lumera_store_api.catalog.domain;

import com.lumera.store.lumera_store_api.catalog.domain.exception.InvalidProductException;
import lombok.Builder;
import lombok.Getter;


import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Getter
public class Product {
    private final UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Category category;
    private boolean active;

    @Builder
    public Product(String name, String description, BigDecimal price, Category category) {
        this(UUID.randomUUID(), name, description, price, category, true);
    }

    private Product(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            Category category,
            boolean active
    ) {
        if (name == null || name.isBlank()) {
            throw new InvalidProductException("Product name cannot be null or blank");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException("Product price cannot be null or negative");

        }

        this.id = Objects.requireNonNull(id, "ID cannot be null");
        updateDetails(name, description, price, category);
        this.active = active;
    }

    public static Product restore(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            Category category,
            boolean active
    ) {
        return new Product(id, name, description, price, category, active);
    }

    public void updateDetails(
            String name,
            String description,
            BigDecimal price,
            Category category
    ) {
        if (name == null || name.isBlank()){
            throw new InvalidProductException("Product name cannot be null or blank");
        }

        String normalizedName = name.strip();

        if (normalizedName.length() > 150) {
            throw new InvalidProductException("Product name cannot be longer than 150");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException("Product price cannot be null or negative");
        }

        BigDecimal normalizedPrice = price.stripTrailingZeros();

        if (normalizedPrice.scale() > 2 || normalizedPrice.precision() - normalizedPrice.scale() > 17) {
            throw  new InvalidProductException("Product price must have at most 17 integer digits and 2 decimal places");
        }

        if (category == null) {
            throw new InvalidProductException("Product category is required");
        }

        this.name = normalizedName;
        this.description = description;
        this.category = category;
        this.price = price;

    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public void updateActive(boolean active) {
        this.active = active;
    }

}
