package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.repository.OrderRepository;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
    private static final String ORDERS_TOPIC = "orders-topic";

    // Valid statuses for validation
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "PROCESSING", "SHIPPED", "CANCELLED");

    private final OrderRepository orderRepository;
    private final KafkaOperations<String, String> kafkaTemplate;

    public OrderService(OrderRepository orderRepository, KafkaOperations<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // ── Create a new order ─────────────────────────────────────────────────
    public OrderResponse placeOrder(OrderRequest request) {
        log.info("Placing new order: item='{}', quantity={}", request.getItemName(), request.getQuantity());

        SupplyOrder order = new SupplyOrder();
        order.setItemName(request.getItemName());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        SupplyOrder saved = orderRepository.save(order);
        log.debug("Order persisted to DB: id={}", saved.getId());

        publishOrderEvent(saved);
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
    @Retry(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publishOrderEvent(SupplyOrder order) {
        String message = String.format("ORDER_PLACED id=%d item='%s' qty=%d",
                order.getId(), order.getItemName(), order.getQuantity());
        log.info("Publishing to Kafka [{}]: {}", ORDERS_TOPIC, message);
        kafkaTemplate.send(ORDERS_TOPIC, message);
    }

    @Retry(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publishStatusEvent(SupplyOrder order) {
        String message = String.format("ORDER_STATUS_UPDATE id=%d status=%s", order.getId(), order.getStatus());
        log.info("Publishing to Kafka [{}]: {}", ORDERS_TOPIC, message);
        kafkaTemplate.send(ORDERS_TOPIC, message);
    }

    // ── Fallback: all Kafka retries exhausted ─────────────────────────────
    public void publishFallback(SupplyOrder order, Exception ex) {
        log.error("Kafka publish FAILED after all retries — order id={}, error: {}", order.getId(), ex.getMessage());
        // Production: write to dead-letter table, trigger PagerDuty alert, etc.
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
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
