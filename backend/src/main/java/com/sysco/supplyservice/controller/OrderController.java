package com.sysco.supplyservice.controller;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller — exposes the supply orders API.
 *
 * Endpoints:
 *   POST   /api/orders                      → Place a new order (201)
 *   GET    /api/orders                      → Get all orders
 *   GET    /api/orders?status=PENDING       → Filter orders by status
 *   GET    /api/orders/{id}                 → Get one order by ID
 *   PATCH  /api/orders/{id}/status          → Update order status
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ── POST /api/orders ───────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("POST /api/orders — item='{}'", request.getItemName());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    // ── GET /api/orders[?status=PENDING] ──────────────────────────────────
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(required = false) String status) {

        if (status != null && !status.isBlank()) {
            log.info("GET /api/orders?status={}", status);
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        }
        log.info("GET /api/orders — all");
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    // ── GET /api/orders/{id} ───────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        log.info("GET /api/orders/{}", id);
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    // ── PATCH /api/orders/{id}/status ──────────────────────────────────────
    // Body: { "status": "SHIPPED" }
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String newStatus = body.get("status");
        log.info("PATCH /api/orders/{}/status — newStatus='{}'", id, newStatus);
        return ResponseEntity.ok(orderService.updateOrderStatus(id, newStatus));
    }
}
