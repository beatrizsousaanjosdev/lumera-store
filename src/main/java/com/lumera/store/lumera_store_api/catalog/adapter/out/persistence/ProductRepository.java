package com.lumera.store.lumera_store_api.catalog.adapter.out.persistence;

import com.lumera.store.lumera_store_api.catalog.domain.Category;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    Page<ProductEntity> findByCategory(
            Category category, Pageable pageable);


    Page<ProductEntity> findByActive(boolean active, Pageable pageable);

    Page<ProductEntity> findByCategoryAndActive(Category category, boolean active, Pageable pageable);

}
