package com.lumera.store.lumera_store_api.inventory.adapter.in.web;

import com.lumera.store.lumera_store_api.inventory.adapter.in.web.exception.InventoryExceptionHandler;
import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.exception.*;
import com.lumera.store.lumera_store_api.inventory.application.port.in.*;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import com.lumera.store.lumera_store_api.inventory.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import(InventoryExceptionHandler.class)
class InventoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InitializeInventoryUsecase initialize;
    @MockitoBean FindInventoryByProductIdUsecase find;
    @MockitoBean AddStockUsecase add;
    @MockitoBean ReserveStockUsecase reserve;
    @MockitoBean ReleaseStockUsecase release;
    @MockitoBean ConfirmStockUsecase confirm;

    private StockMovementUsecase movement(String route) {
        return switch (route) {
            case "entries" -> add;
            case "reservations" -> reserve;
            case "reservations/release" -> release;
            case "reservations/confirm" -> confirm;
            default -> throw new IllegalArgumentException(route);
        };
    }

    @Test
    void shouldInitializeInventory() throws Exception {
        UUID id = UUID.randomUUID();
        when(initialize.execute(id)).thenReturn(new Inventory(id));
        mvc.perform(put("/inventories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(id.toString()))
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.availableQuantity").value(0));
        verify(initialize).execute(id);
    }

    @Test
    void shouldFindInventoryAndReturnComputedAvailability() throws Exception {
        UUID id = UUID.randomUUID();
        when(find.execute(id)).thenReturn(Inventory.restore(id, 10, 4));
        mvc.perform(get("/inventories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQuantity").value(4))
                .andExpect(jsonPath("$.availableQuantity").value(6));
    }

    @ParameterizedTest
    @ValueSource(strings = {"entries", "reservations", "reservations/release", "reservations/confirm"})
    void shouldMapEachMovementToItsSpecificUsecase(String route) throws Exception {
        UUID id = UUID.randomUUID();
        var command = new StockMovementCommand(id, 2);
        when(movement(route).execute(command)).thenReturn(Inventory.restore(id, 10, 4));
        mvc.perform(post("/inventories/{id}/" + route, id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(id.toString()))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.availableQuantity").value(6));
        verify(movement(route)).execute(command);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of("entries", "reservations", "reservations/release", "reservations/confirm")
                .flatMap(route -> Stream.of("{}", "{\"amount\":null}", "{\"amount\":0}",
                                "{\"amount\":-1}", "{\"amount\":\"abc\"}", "{")
                        .map(body -> Arguments.of(route, body)));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void shouldRejectInvalidBodyWithoutCallingUsecase(String route, String body) throws Exception {
        mvc.perform(post("/inventories/{id}/" + route, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(add, reserve, release, confirm);
    }

    @Test
    void shouldReportAmountValidationDetails() throws Exception {
        mvc.perform(post("/inventories/{id}/entries", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void shouldRejectInvalidUuidBeforeLookup() throws Exception {
        mvc.perform(get("/inventories/abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(find);
    }

    @Test
    void shouldReturn404ForMissingInventory() throws Exception {
        UUID id = UUID.randomUUID();
        when(find.execute(id)).thenThrow(new InventoryNotFoundException(id));
        mvc.perform(get("/inventories/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Inventory not found"))
                .andExpect(jsonPath("$.instance").value("/inventories/" + id));
    }

    @Test
    void shouldReturn409ForInsufficientStock() throws Exception {
        UUID id = UUID.randomUUID();
        when(reserve.execute(new StockMovementCommand(id, 2))).thenThrow(new InsufficientStockException(2, 1));
        mvc.perform(post("/inventories/{id}/reservations", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"));
    }

    @Test
    void shouldReturn400ForDomainViolation() throws Exception {
        UUID id = UUID.randomUUID();
        when(release.execute(new StockMovementCommand(id, 2)))
                .thenThrow(new InvalidInventoryException("Release exceeds reserved quantity"));
        mvc.perform(post("/inventories/{id}/reservations/release", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid inventory"));
    }

    @Test
    void shouldReturn409ForLockConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(add.execute(new StockMovementCommand(id, 2)))
                .thenThrow(new CannotAcquireLockException("database lock details"));
        mvc.perform(post("/inventories/{id}/entries", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Inventory concurrency conflict"));
    }

    @Test
    void shouldHideInitializationFailureDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(initialize.execute(id)).thenThrow(new InventoryInitializationException(id));
        mvc.perform(put("/inventories/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The operation could not be completed."));
    }

    @Test
    void shouldHideUnexpectedFailureDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(find.execute(id)).thenThrow(new IllegalStateException("secret internal detail"));
        mvc.perform(get("/inventories/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The operation could not be completed."));
    }
}
