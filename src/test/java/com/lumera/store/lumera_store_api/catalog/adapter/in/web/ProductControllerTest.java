package com.lumera.store.lumera_store_api.catalog.adapter.in.web;

import com.lumera.store.lumera_store_api.catalog.adapter.in.web.exception.ProductExceptionHandler;
import com.lumera.store.lumera_store_api.catalog.application.exception.InvalidPaginationException;
import com.lumera.store.lumera_store_api.catalog.application.exception.ProductNotFoundException;
import com.lumera.store.lumera_store_api.catalog.application.model.ProductPage;
import com.lumera.store.lumera_store_api.catalog.application.port.in.CreateProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.FindProductByIdUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.ListProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.UpdateProductActiveUsecase;
import com.lumera.store.lumera_store_api.catalog.application.port.in.UpdateProductUsecase;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.catalog.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(ProductExceptionHandler.class)
class ProductControllerTest {

    @MockitoBean
    private ListProductsUsecase listProductsUsecase;

    @MockitoBean
    private UpdateProductActiveUsecase updateProductActiveUsecase;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateProductsUsecase createProductsUsecase;

    @MockitoBean
    private FindProductByIdUsecase findProductByIdUsecase;

    @MockitoBean
    private UpdateProductUsecase updateProductUseCase;

    @Test
    void shouldReturnProductWhenFound() throws Exception {
        Product product = new Product(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        when(findProductByIdUsecase.execute(product.getId()))
                .thenReturn(product);

        mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id")
                        .value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Teclado"))
                .andExpect(jsonPath("$.description").value("Teclado USB"))
                .andExpect(jsonPath("$.price").value(249.90))
                .andExpect(jsonPath("$.category").value("ACCESSORIES"))
                .andExpect(jsonPath("$.active").value(true));

        verify(findProductByIdUsecase).execute(product.getId());
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();

        when(findProductByIdUsecase.execute(id))
                .thenThrow(new ProductNotFoundException(id));

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail")
                        .value("Product not found by ID: " + id))
                .andExpect(jsonPath("$.instance")
                        .value("/products/" + id));
    }

    @Test
    void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
        mockMvc.perform(get("/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/products/abc"));

        verifyNoInteractions(findProductByIdUsecase);
    }

    @Test
    void shouldCreateProduct() throws Exception {
        Product product = new Product(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );

        when(createProductsUsecase.execute(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        )).thenReturn(product);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Teclado",
                              "description": "Teclado USB",
                              "price": 249.90,
                              "category": "ACCESSORIES"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id")
                        .value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Teclado"))
                .andExpect(jsonPath("$.description").value("Teclado USB"))
                .andExpect(jsonPath("$.price").value(249.90))
                .andExpect(jsonPath("$.category").value("ACCESSORIES"))
                .andExpect(jsonPath("$.active").value(true));

        verify(createProductsUsecase).execute(
                "Teclado",
                "Teclado USB",
                new BigDecimal("249.90"),
                Category.ACCESSORIES
        );
    }

    @Test
    void shouldRejectNegativePriceBeforeCallingUseCase() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Teclado",
                              "description": "Teclado USB",
                              "price": -10.00,
                              "category": "ACCESSORIES"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.instance").value("/products"))
                .andExpect(jsonPath("$.errors[0].field").value("price"));

        verifyNoInteractions(createProductsUsecase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void shouldRejectUnknownCategory(String method) throws Exception {
        var request = method.equals("POST")
                ? post("/products")
                : put("/products/{id}", UUID.randomUUID());

        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Teclado","price":249.90,"category":"INVALID"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(createProductsUsecase, updateProductUseCase);
    }

    @Test
    void shouldRejectMissingCategoryBeforeCallingUseCase() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Teclado","price":249.90}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        hasItem("category")));
        verifyNoInteractions(createProductsUsecase);
    }

    @Test
    void shouldUpdateProductCategory() throws Exception {
        UUID id = UUID.randomUUID();
        Product product = Product.restore(id, "Mouse", "Sem fio",
                new BigDecimal("129.90"), Category.PERIPHERALS, false);
        when(updateProductUseCase.execute(id, "Mouse", "Sem fio",
                new BigDecimal("129.90"), Category.PERIPHERALS)).thenReturn(product);

        mockMvc.perform(put("/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mouse","description":"Sem fio",
                                 "price":129.90,"category":"PERIPHERALS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.category").value("PERIPHERALS"))
                .andExpect(jsonPath("$.active").value(false));
    }


    @Test
    void shouldListProductsWithDefaults() throws Exception {
        var product = new Product("Mouse", null, BigDecimal.TEN, Category.PERIPHERALS);
        when(listProductsUsecase.execute(0, 20, null, null)).thenReturn(
                new ProductPage(
                        List.of(product), 0, 20, 1, 1));
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("PERIPHERALS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void shouldFilterAndReturnEmptyPage() throws Exception {
        when(listProductsUsecase.execute(2, 5, Category.MONITORS, null)).thenReturn(
                new ProductPage(
                        List.of(), 2, 5, 0, 0));
        mockMvc.perform(get("/products").param("page", "2").param("size", "5")
                        .param("category", "MONITORS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2));
        verify(listProductsUsecase).execute(2, 5, Category.MONITORS, null);
    }

    @Test
    void shouldRejectInvalidListParameters() throws Exception {
        mockMvc.perform(get("/products").param("category", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/products").param("page", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listProductsUsecase);
    }

    @Test
    void shouldFormatInvalidPagination() throws Exception {
        when(listProductsUsecase.execute(-1, 20, null, null)).thenThrow(
                new InvalidPaginationException());
        mockMvc.perform(get("/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid pagination"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPatchActiveStatus(boolean active) throws Exception {
        UUID id = UUID.randomUUID();
        var product = Product.restore(id, "Mouse", null, BigDecimal.TEN, Category.OTHER, active);
        when(updateProductActiveUsecase.execute(id, active)).thenReturn(product);
        mockMvc.perform(patch("/products/{id}/active", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":" + active + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(active))
                .andExpect(jsonPath("$.id").value(id.toString()));
        verify(updateProductActiveUsecase).execute(id, active);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"active\":null}"})
    void shouldRejectMissingActiveStatus(String body) throws Exception {
        mockMvc.perform(patch("/products/{id}/active", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(updateProductActiveUsecase);
    }

    @Test
    void shouldReturn404ForMissingProductOnStatusUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateProductActiveUsecase.execute(id, false)).thenThrow(new ProductNotFoundException(id));
        mockMvc.perform(patch("/products/{id}/active", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldListByActiveStatusAndCategory(boolean active) throws Exception {
        when(listProductsUsecase.execute(0, 20, Category.OTHER, active))
                .thenReturn(new ProductPage(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get("/products").param("active", Boolean.toString(active))
                        .param("category", "OTHER"))
                .andExpect(status().isOk());
        verify(listProductsUsecase).execute(0, 20, Category.OTHER, active);
    }

    @Test
    void shouldRejectInvalidActiveFilter() throws Exception {
        mockMvc.perform(get("/products").param("active", "INVALID"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listProductsUsecase);
    }

}
