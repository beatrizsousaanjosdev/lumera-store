package com.lumera.store.lumera_store_api.inventory.domain;

import com.lumera.store.lumera_store_api.inventory.domain.exception.InsufficientStockException;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidInventoryException;
import lombok.Getter;

import java.util.UUID;

@Getter
public class Inventory {

    private final UUID productId;
    private int quantity;
    private int reservedQuantity;

    public Inventory(UUID productId){
        this(productId, 0, 0);
    }

    private Inventory(
            UUID productId,
            int quantity,
            int reservedQuantity) {

        if (productId == null) {
            throw new InvalidInventoryException(
                    "Product ID is required"
            );
        }

        if (quantity < 0) {
            throw new InvalidInventoryException("Stock quantity cannot be negative");
        }

        if (reservedQuantity < 0 || reservedQuantity > quantity) {
            throw new InvalidInventoryException("Stock quantity must be between zero and total quantity");
        }

        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
    }

    public void release(int amount) {
        if (amount <= 0) {
            throw new InvalidInventoryException(
                    "Release amount must be greater than zero"
            );
        }

        if (amount > reservedQuantity) {
            throw new InvalidInventoryException(
                    "Release amount cannot exceed reserved quantity"
            );
        }

        this.reservedQuantity -= amount;
    }

    public void confirmReservation(int amount) {
        if (amount <= 0) {
            throw new InvalidInventoryException(
                    "Confirmation amount must be greater than zero"
            );
        }

        if (amount > reservedQuantity) {
            throw new InvalidInventoryException(
                    "Confirmation amount cannot exceed reserved quantity"
            );
        }

        this.quantity -= amount;
        this.reservedQuantity -= amount;
    }

    public static Inventory restore(
            UUID productId,
            int quantity,
            int reservedQuantity
    ){
        return new Inventory(productId, quantity, reservedQuantity);
    }

    public void addStock(int amount) {
        if (amount <= 0) {
            throw  new InvalidInventoryException("Stock amount must be greater than zero");
        }

        try {
            this.quantity = Math.addExact(this.quantity, amount);
        } catch (ArithmeticException e) {
            throw   new InvalidInventoryException("Stock quantity exceeds the supported limit.");
        }
    }

    public void reserve(int amount) {
        if (amount <= 0) {
            throw new InvalidInventoryException(
                    "Reservation amount must be greater than zero"
            );
        }

        if (amount > getAvailableQuantity()) {
            throw new InsufficientStockException(
                    amount, getAvailableQuantity()
            );
        }

        this.reservedQuantity += amount;
    }

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }
}
