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

/**
 * REST controller exposing the orders API.
 *
 * Endpoints:
 *   POST   /api/orders              → Create a new order
 *   GET    /api/orders              → Get all orders
 *   GET    /api/orders/{id}         → Get one order by ID
 *   PATCH  /api/orders/{id}/status  → Update order status
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
    // @Valid triggers the validation rules defined in OrderRequest (e.g. @NotBlank)
    // Returns 201 Created on success
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("POST /api/orders - creating order for '{}'", request.getItemName());
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/orders ────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("GET /api/orders - fetching all orders");
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    // ── GET /api/orders/{id} ───────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        log.info("GET /api/orders/{} - fetching single order", id);
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    // ── PATCH /api/orders/{id}/status ──────────────────────────────────────
    // Updates only the status field: PENDING → PROCESSING → SHIPPED
    // Example body: { "status": "SHIPPED" }
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {

        String newStatus = body.get("status");
        log.info("PATCH /api/orders/{}/status - new status: '{}'", id, newStatus);
        return ResponseEntity.ok(orderService.updateOrderStatus(id, newStatus));
    }
}
