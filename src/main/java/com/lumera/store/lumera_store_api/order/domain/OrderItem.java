package com.lumera.store.lumera_store_api.order.domain;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record OrderItem(
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
    public OrderItem{
        if(productId == null){
            throw new InvalidOrderException("Product id is required");
        }

        if(productName == null || productName.isBlank()){
            throw new InvalidOrderException("Product name is required");
        }

        if (quantity <= 0) {
            throw new InvalidOrderException(
                    "Item quantity must be greater than zero"
            );
        }

        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new InvalidOrderException(
                    "Unit price must be greater than zero"
            );
        }

        try {
            unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidOrderException(
                    "Unit price must have at most two decimal places"
            );
        }

        productName = productName.strip();

        if (productName.length() > 150) {
            throw new InvalidOrderException(
                    "Product name cannot exceed 150 characters"
            );
        }

        if (unitPrice.precision() > 19) {
            throw new InvalidOrderException(
                    "Unit price must have at most 17 integer digits"
            );
        }

    }

    public BigDecimal subTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
