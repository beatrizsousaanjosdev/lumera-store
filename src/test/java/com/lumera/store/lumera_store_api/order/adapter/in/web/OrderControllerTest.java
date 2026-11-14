package com.lumera.store.lumera_store_api.order.adapter.in.web;

import com.lumera.store.lumera_store_api.order.adapter.in.web.exception.OrderExceptionHandler;
import com.lumera.store.lumera_store_api.order.application.command.*;
import com.lumera.store.lumera_store_api.order.application.exception.*;
import com.lumera.store.lumera_store_api.order.application.port.in.*;
import com.lumera.store.lumera_store_api.order.domain.*;
import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(OrderExceptionHandler.class)
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CreateOrderUsecase create;
    @MockitoBean FindOrderByIdUsecase find;

    private Order order(UUID productId) {
        return new Order(List.of(new OrderItem(productId, "Mouse", 2, new BigDecimal("10.25"))));
    }

    @Test
    void shouldCreateOrderAndReturnLocationAndSnapshots() throws Exception {
        UUID productId = UUID.randomUUID();
        var order = order(productId);
        var command = new CreateOrderCommand(List.of(new CreateOrderItemCommand(productId, 2)));
        when(create.execute(command)).thenReturn(order);
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/orders/" + order.getId()))
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING_STOCK"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.total").value(20.50))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.25))
                .andExpect(jsonPath("$.items[0].subTotal").value(20.50));
        verify(create).execute(command);
    }

    @Test
    void shouldFindOrder() throws Exception {
        var order = order(UUID.randomUUID());
        when(find.execute(order.getId())).thenReturn(order);
        mvc.perform(get("/orders/{id}", order.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.total").value(20.50));
    }

    static Stream<String> invalidRequests() {
        String id = UUID.randomUUID().toString();
        return Stream.of("{", "{}", "{\"items\":null}", "{\"items\":[]}", "{\"items\":[null]}",
                "{\"items\":[{\"quantity\":1}]}",
                "{\"items\":[{\"productId\":\"abc\",\"quantity\":1}]}",
                "{\"items\":[{\"productId\":\"" + id + "\"}]}",
                "{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":0}]}",
                "{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":-1}]}",
                "{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":1},{\"productId\":\"" + id + "\",\"quantity\":2}]}");
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void shouldRejectInvalidBodyBeforeCallingUsecase(String body) throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(create);
    }

    @Test
    void shouldIncludeFieldValidationDetails() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void shouldRejectMalformedIdBeforeLookup() throws Exception {
        mvc.perform(get("/orders/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(find);
    }

    @Test
    void shouldReturn404ForMissingOrder() throws Exception {
        UUID id = UUID.randomUUID();
        when(find.execute(id)).thenThrow(new OrderNotFoundException(id));
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.instance").value("/orders/" + id));
    }

    @Test
    void shouldReturn404ForMissingProduct() throws Exception {
        assertCreateFailure(new OrderProductNotFoundException(UUID.randomUUID()), 404);
    }

    @Test
    void shouldReturn409ForInactiveProduct() throws Exception {
        assertCreateFailure(new InactiveProductException(UUID.randomUUID()), 409);
    }

    @Test
    void shouldReturn400ForDomainViolation() throws Exception {
        assertCreateFailure(new InvalidOrderException("Invalid order"), 400);
    }

    @Test
    void shouldHideUnexpectedFailureDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(find.execute(id)).thenThrow(new IllegalStateException("internal database detail"));
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The operation could not be completed."));
    }

    private void assertCreateFailure(RuntimeException exception, int status) throws Exception {
        when(create.execute(any())).thenThrow(exception);
        UUID id = UUID.randomUUID();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":1}]}"))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }
}
