package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.idempotency.IdempotencyRecord;
import com.sysco.supplyservice.idempotency.IdempotencyRecordRepository;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.outbox.OutboxService;
import com.sysco.supplyservice.repository.OrderRepository;
import com.sysco.supplyservice.saga.SagaEventType;
import com.sysco.supplyservice.saga.SagaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxService outboxService;

    public OrderService(
            OrderRepository orderRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxService = outboxService;
    }

    // ── Create a new order ─────────────────────────────────────────────────
    @Transactional
    public CreateOrderResult placeOrder(OrderRequest request, String idempotencyKey) {
        log.info("Placing new order: item='{}', quantity={}", request.getItemName(), request.getQuantity());
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = buildRequestHash(request);

        if (normalizedKey != null) {
            IdempotencyRecord existingRecord = idempotencyRecordRepository.findByIdempotencyKey(normalizedKey)
                    .orElse(null);
            if (existingRecord != null) {
                validateMatchingRequest(existingRecord, requestHash, normalizedKey);
                return new CreateOrderResult(
                        toResponse(findOrderOrThrow(existingRecord.getOrderId())),
                        false
                );
            }
        }

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

        if (normalizedKey != null) {
            persistIdempotencyRecord(normalizedKey, requestHash, saved.getId());
        }
        return new CreateOrderResult(toResponse(saved), true);
    }

    // ── Get all orders ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        log.debug("Fetching all orders");
        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Get orders filtered by status ──────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStatus(String status) {
        log.debug("Fetching orders with status='{}'", status);
        return orderRepository.findByStatus(status.toUpperCase())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Get a single order by ID ───────────────────────────────────────────
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        log.debug("Fetching order id={}", id);
        return toResponse(findOrderOrThrow(id));
    }

    // ── Update the status of an order ──────────────────────────────────────
    @Transactional
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
        outboxService.enqueueOperationalEvent(order.getId(), "ORDER_PLACED", message);
    }

    public void publishStatusEvent(SupplyOrder order) {
        String message = String.format("ORDER_STATUS_UPDATE id=%d status=%s", order.getId(), order.getStatus());
        outboxService.enqueueOperationalEvent(order.getId(), "ORDER_STATUS_UPDATE", message);
    }

    public void publishSagaStarted(SupplyOrder order) {
        outboxService.enqueueSagaEvent(new SagaMessage(
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

    private void persistIdempotencyRecord(String idempotencyKey, String requestHash, Long orderId) {
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(idempotencyKey);
            record.setRequestHash(requestHash);
            record.setOrderId(orderId);
            idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecord existingRecord = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            validateMatchingRequest(existingRecord, requestHash, idempotencyKey);
        }
    }

    private void validateMatchingRequest(IdempotencyRecord existingRecord, String requestHash, String idempotencyKey) {
        if (!existingRecord.getRequestHash().equals(requestHash)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private String buildRequestHash(OrderRequest request) {
        String payload = "%s|%d".formatted(
                request.getItemName().trim().toLowerCase(Locale.ROOT),
                request.getQuantity()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
