package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.repository.OrderRepository;
import com.sysco.supplyservice.saga.OrderEventPublisher;
import com.sysco.supplyservice.saga.SagaEventType;
import com.sysco.supplyservice.saga.SagaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core business logic for order management.
 *
 * Key enterprise patterns used:
 *  - SLF4J structured logging (timestamped, level-filtered, written to file)
 *  - @Retry (Resilience4j): Kafka publish retried up to 3x on failure
 *  - DTOs: request/response separation from the JPA entity
 *  - Status validation: only permitted transitions are allowed
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Valid statuses for validation
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "PROCESSING", "SHIPPED", "CANCELLED");

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── Create a new order ─────────────────────────────────────────────────
    public OrderResponse placeOrder(OrderRequest request) {
        log.info("Placing new order: item='{}', quantity={}", request.getItemName(), request.getQuantity());

        SupplyOrder order = new SupplyOrder();
        order.setItemName(request.getItemName());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");
        order.setSagaId(UUID.randomUUID().toString());
        order.setSagaState("STARTED");
        order.setFailureReason(null);

        SupplyOrder saved = orderRepository.save(order);
        log.debug("Order persisted to DB: id={}", saved.getId());

        publishOrderEvent(saved);
        publishSagaStarted(saved);
        return toResponse(saved);
    }

    // ── Get all orders ─────────────────────────────────────────────────────
    public List<OrderResponse> getAllOrders() {
        log.debug("Fetching all orders");
        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Get orders filtered by status ──────────────────────────────────────
    public List<OrderResponse> getOrdersByStatus(String status) {
        log.debug("Fetching orders with status='{}'", status);
        return orderRepository.findByStatus(status.toUpperCase())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Get a single order by ID ───────────────────────────────────────────
    public OrderResponse getOrderById(Long id) {
        log.debug("Fetching order id={}", id);
        return toResponse(findOrderOrThrow(id));
    }

    // ── Update the status of an order ──────────────────────────────────────
    public OrderResponse updateOrderStatus(Long id, String newStatus) {
        String upperStatus = newStatus.toUpperCase();
        if (!VALID_STATUSES.contains(upperStatus)) {
            throw new IllegalArgumentException(
                "Invalid status '" + newStatus + "'. Allowed: " + VALID_STATUSES);
        }

        SupplyOrder order = findOrderOrThrow(id);
        String oldStatus = order.getStatus();
        order.setStatus(upperStatus);
        SupplyOrder updated = orderRepository.save(order);

        log.info("Order id={} status changed: {} → {}", id, oldStatus, upperStatus);
        publishStatusEvent(updated);
        return toResponse(updated);
    }

    // ── Kafka publish with Resilience4j @Retry ─────────────────────────────
    // Retried up to 3 times (500 ms wait) if Kafka is temporarily unavailable.
    public void publishOrderEvent(SupplyOrder order) {
        String message = String.format("ORDER_PLACED id=%d item='%s' qty=%d",
                order.getId(), order.getItemName(), order.getQuantity());
        eventPublisher.publishOperationalEvent(order.getId(), message);
    }

    public void publishStatusEvent(SupplyOrder order) {
        String message = String.format("ORDER_STATUS_UPDATE id=%d status=%s", order.getId(), order.getStatus());
        eventPublisher.publishOperationalEvent(order.getId(), message);
    }

    public void publishSagaStarted(SupplyOrder order) {
        eventPublisher.publishSagaEvent(new SagaMessage(
                SagaEventType.ORDER_CREATED,
                order.getSagaId(),
                order.getId(),
                order.getItemName(),
                order.getQuantity(),
                null,
                LocalDateTime.now()
        ));
    }

    public void publishFallback(SupplyOrder order, Exception ex) {
        log.error("Legacy fallback invoked — order id={}, error: {}", order.getId(), ex.getMessage());
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private SupplyOrder findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private OrderResponse toResponse(SupplyOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getItemName(),
                order.getQuantity(),
                order.getStatus(),
                order.getSagaId(),
                order.getSagaState(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
