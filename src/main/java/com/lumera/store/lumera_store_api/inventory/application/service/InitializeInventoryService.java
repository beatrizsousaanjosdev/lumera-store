package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.exception.InventoryInitializationException;
import com.lumera.store.lumera_store_api.inventory.application.port.in.InitializeInventoryUsecase;
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
public class InitializeInventoryService implements InitializeInventoryUsecase {

    private final InventoryRepositoryPort port;

    @Override
    @Transactional
    public Inventory execute(UUID productId) {
        if(productId == null){
            throw new InvalidInventoryException("Product ID is required");
        }

        log.debug("Initializing inventory: productId={}", productId);

        port.initializeIfAbsent(productId);

        return port.findByProductId(productId)
                .orElseThrow(() -> new InventoryInitializationException(productId));
    }
}
