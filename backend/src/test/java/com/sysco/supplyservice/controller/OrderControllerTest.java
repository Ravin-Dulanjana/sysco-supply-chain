package com.sysco.supplyservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.GlobalExceptionHandler;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController Web Layer Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private final LocalDateTime now = LocalDateTime.now();

    private OrderResponse sampleResponse() {
        return new OrderResponse(1L, "Widget A", 10, "PENDING", now, now);
    }

    // ── POST /api/orders ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders: returns 201 with created order")
    void createOrder_returns201() throws Exception {
        OrderRequest req = new OrderRequest();
        req.setItemName("Widget A");
        req.setQuantity(10);

        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.itemName").value("Widget A"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/orders: returns 400 for blank itemName")
    void createOrder_returns400ForBlankItemName() throws Exception {
        OrderRequest req = new OrderRequest();
        req.setItemName("");
        req.setQuantity(5);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/orders: returns 400 for quantity < 1")
    void createOrder_returns400ForZeroQuantity() throws Exception {
        OrderRequest req = new OrderRequest();
        req.setItemName("Widget X");
        req.setQuantity(0);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/orders: returns 400 for null quantity")
    void createOrder_returns400ForNullQuantity() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\": \"Widget X\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/orders ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders: returns 200 with list of orders")
    void getAllOrders_returns200() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].itemName").value("Widget A"));
    }

    @Test
    @DisplayName("GET /api/orders: returns empty list when no orders")
    void getAllOrders_returnsEmptyList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/orders?status=PENDING: returns filtered orders")
    void getOrdersByStatus_returnsFiltered() throws Exception {
        when(orderService.getOrdersByStatus("PENDING")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── GET /api/orders/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id}: returns 200 when found")
    void getOrderById_returns200() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.itemName").value("Widget A"));
    }

    @Test
    @DisplayName("GET /api/orders/{id}: returns 404 when not found")
    void getOrderById_returns404() throws Exception {
        when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value(containsString("99")));
    }

    // ── PATCH /api/orders/{id}/status ──────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/orders/{id}/status: returns 200 with updated status")
    void updateStatus_returns200() throws Exception {
        OrderResponse updated = new OrderResponse(1L, "Widget A", 10, "SHIPPED", now, now);
        when(orderService.updateOrderStatus(eq(1L), eq("SHIPPED"))).thenReturn(updated);

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SHIPPED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/status: returns 400 for invalid status")
    void updateStatus_returns400ForInvalidStatus() throws Exception {
        when(orderService.updateOrderStatus(eq(1L), eq("FLYING")))
                .thenThrow(new IllegalArgumentException("Invalid status 'FLYING'"));

        mockMvc.perform(patch("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FLYING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("FLYING")));
    }
}
