package com.sysco.supplyservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.GlobalExceptionHandler;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.security.JwtService;
import com.sysco.supplyservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer slice tests for OrderController.
 * Uses MockMvc — no real HTTP server, no DB, no Kafka.
 * OrderService is mocked, so we test only the HTTP layer behaviour.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderResponse sample() {
        return new OrderResponse(1L, "Widget A", 10, "PENDING", LocalDateTime.now(), LocalDateTime.now());
    }

    // ── POST /api/orders ───────────────────────────────────────────────────

    @Test
    void createOrder_returns201WithBody() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(sample());

        OrderRequest req = new OrderRequest();
        req.setItemName("Widget A");
        req.setQuantity(10);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOrder_returns400WhenItemNameBlank() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"\",\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createOrder_returns400WhenQuantityMissing() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Widget X\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/orders ────────────────────────────────────────────────────

    @Test
    void getAllOrders_returns200WithList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].itemName").value("Widget A"));
    }

    @Test
    void getOrdersByStatus_returnsFilteredList() throws Exception {
        when(orderService.getOrdersByStatus("PENDING")).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── GET /api/orders/{id} ───────────────────────────────────────────────

    @Test
    void getOrderById_returns200WhenFound() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getOrderById_returns404WhenNotFound() throws Exception {
        when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error", containsString("99")));
    }

    // ── PATCH /api/orders/{id}/status ──────────────────────────────────────

    @Test
    void updateStatus_returns200WithNewStatus() throws Exception {
        OrderResponse updated = new OrderResponse(1L, "Widget A", 10, "SHIPPED", LocalDateTime.now(), LocalDateTime.now());
        when(orderService.updateOrderStatus(eq(1L), eq("SHIPPED"))).thenReturn(updated);

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SHIPPED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void updateStatus_returns400ForInvalidStatus() throws Exception {
        when(orderService.updateOrderStatus(eq(1L), eq("FLYING")))
                .thenThrow(new IllegalArgumentException("Invalid status 'FLYING'"));

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FLYING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("FLYING")));
    }
}
