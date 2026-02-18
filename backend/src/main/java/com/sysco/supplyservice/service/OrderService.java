package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.repository.OrderRepository;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core business logic for order management.
 *
 * Key patterns used here:
 *  - SLF4J logger instead of System.out.println (goes to console + log file)
 *  - @Retry from Resilience4j: if Kafka publish fails, it retries up to 3 times
 *    before giving up (configured in application.yaml)
 *  - DTOs: accepts OrderRequest, returns OrderResponse (never exposes entity directly)
 */
@Service
public class OrderService {

    // SLF4J logger — outputs to console with timestamps and log levels
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String ORDERS_TOPIC = "orders-topic";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Constructor injection (preferred over @Autowired on fields)
    public OrderService(OrderRepository orderRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // ── Create a new order ─────────────────────────────────────────────────
    public OrderResponse placeOrder(OrderRequest request) {
        log.info("Placing new order for item '{}', quantity {}", request.getItemName(), request.getQuantity());

        SupplyOrder order = new SupplyOrder();
        order.setItemName(request.getItemName());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        SupplyOrder saved = orderRepository.save(order);
        log.debug("Order saved to DB with id={}", saved.getId());

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

    // ── Get a single order by ID ───────────────────────────────────────────
    public OrderResponse getOrderById(Long id) {
        log.debug("Fetching order id={}", id);
        SupplyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return toResponse(order);
    }

    // ── Update the status of an order ─────────────────────────────────────
    // e.g. PENDING → PROCESSING → SHIPPED
    public OrderResponse updateOrderStatus(Long id, String newStatus) {
        log.info("Updating order id={} to status '{}'", id, newStatus);

        SupplyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        String oldStatus = order.getStatus();
        order.setStatus(newStatus);
        SupplyOrder updated = orderRepository.save(order);

        log.info("Order id={} status changed: {} → {}", id, oldStatus, newStatus);

        // Notify Kafka about the status change
        publishStatusEvent(updated);

        return toResponse(updated);
    }

    // ── Kafka publish with Resilience4j @Retry ────────────────────────────
    // If Kafka is temporarily unavailable, this method will be retried
    // up to 3 times (configured as "kafkaPublish" in application.yaml)
    @Retry(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publishOrderEvent(SupplyOrder order) {
        String message = "ORDER_PLACED: " + order.getItemName() + " (Qty: " + order.getQuantity() + ", Id: " + order.getId() + ")";
        log.info("Publishing to Kafka topic '{}': {}", ORDERS_TOPIC, message);
        kafkaTemplate.send(ORDERS_TOPIC, message);
    }

    @Retry(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publishStatusEvent(SupplyOrder order) {
        String message = "ORDER_STATUS_UPDATE: id=" + order.getId() + " status=" + order.getStatus();
        log.info("Publishing to Kafka topic '{}': {}", ORDERS_TOPIC, message);
        kafkaTemplate.send(ORDERS_TOPIC, message);
    }

    // ── Fallback: called if all Kafka retries are exhausted ───────────────
    // The app doesn't crash — it logs the failure and moves on.
    public void publishFallback(SupplyOrder order, Exception ex) {
        log.error("Kafka publish failed after all retries for order id={}. Error: {}", order.getId(), ex.getMessage());
        // In production you might: save to a "dead letter" table, trigger an alert, etc.
    }

    // ── Helper: convert entity → response DTO ────────────────────────────
    private OrderResponse toResponse(SupplyOrder order) {
        return new OrderResponse(order.getId(), order.getItemName(), order.getQuantity(), order.getStatus());
    }
}
