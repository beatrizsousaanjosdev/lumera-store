package com.lumera.store.lumera_store_api.catalog.adapter.in.web.dto;

import java.util.List;

public record ProductPageResponseDto(List<ProductResponseDto> content, int page, int size,
                                     long totalElements, int totalPages) {
}
