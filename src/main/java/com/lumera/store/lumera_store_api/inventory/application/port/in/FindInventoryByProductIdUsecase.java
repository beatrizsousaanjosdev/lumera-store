package com.lumera.store.lumera_store_api.inventory.application.port.in;

import com.lumera.store.lumera_store_api.inventory.domain.Inventory;

import java.util.UUID;

public interface FindInventoryByProductIdUsecase {

    Inventory execute(UUID productId);
}
