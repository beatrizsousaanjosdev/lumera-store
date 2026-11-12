package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.exception.InventoryNotFoundException;
import com.lumera.store.lumera_store_api.inventory.application.port.in.FindInventoryByProductIdUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidInventoryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class FindInventoryByProductIdService implements FindInventoryByProductIdUsecase {

    private final InventoryRepositoryPort port;

    @Override
    @Transactional(readOnly = true)
    public Inventory execute(UUID productId) {
        if (productId == null) {
            throw new InvalidInventoryException("Product ID is required");
        }
        log.debug("Finding Inventory by product id {}", productId);

        return port.findByProductId(productId)
                .orElseThrow(() -> {
                    log.debug("Inventory not found: productId={}", productId);

                    return new InventoryNotFoundException(productId);
                });
    }
}
