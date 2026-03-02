package com.sysco.supplyservice.saga;

import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orderRepository;
    private final SagaMessageSerializer serializer;
    private final OrderEventPublisher eventPublisher;

    public OrderSagaOrchestrator(
            OrderRepository orderRepository,
            SagaMessageSerializer serializer,
            OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.serializer = serializer;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "${app.saga.topic:order-saga-topic}", groupId = "order-saga-orchestrator-group")
    public void onSagaMessage(String rawMessage) {
        SagaMessage message = serializer.fromJson(rawMessage);
        log.info("SAGA orchestrator received event={} sagaId={} orderId={}",
                message.eventType(), message.sagaId(), message.orderId());

        switch (message.eventType()) {
            case ORDER_CREATED -> handleOrderCreated(message);
            case INVENTORY_RESERVED -> handleInventoryReserved(message);
            case INVENTORY_REJECTED -> handleInventoryRejected(message);
            case PAYMENT_COMPLETED -> handlePaymentCompleted(message);
            case PAYMENT_FAILED -> handlePaymentFailed(message);
            case INVENTORY_RELEASED -> handleInventoryReleased(message);
            default -> log.debug("Ignoring non-orchestrator saga event={}", message.eventType());
        }
    }

    private void handleOrderCreated(SagaMessage message) {
        eventPublisher.publishSagaEvent(copyWithType(message, SagaEventType.RESERVE_INVENTORY, null));
    }

    private void handleInventoryReserved(SagaMessage message) {
        mutateOrder(message.orderId(), order -> {
            order.setSagaState("INVENTORY_RESERVED");
            order.setFailureReason(null);
        });
        eventPublisher.publishSagaEvent(copyWithType(message, SagaEventType.REQUEST_PAYMENT, null));
    }

    private void handleInventoryRejected(SagaMessage message) {
        SupplyOrder order = mutateOrder(message.orderId(), current -> {
            current.setStatus("CANCELLED");
            current.setSagaState("FAILED");
            current.setFailureReason(message.reason());
        });
        eventPublisher.publishOperationalEvent(order.getId(),
                "ORDER_CANCELLED id=%d sagaState=%s reason=%s".formatted(
                        order.getId(), order.getSagaState(), order.getFailureReason()));
    }

    private void handlePaymentCompleted(SagaMessage message) {
        SupplyOrder order = mutateOrder(message.orderId(), current -> {
            current.setStatus("PROCESSING");
            current.setSagaState("COMPLETED");
            current.setFailureReason(null);
        });
        eventPublisher.publishOperationalEvent(order.getId(),
                "ORDER_READY_FOR_FULFILLMENT id=%d status=%s sagaState=%s".formatted(
                        order.getId(), order.getStatus(), order.getSagaState()));
    }

    private void handlePaymentFailed(SagaMessage message) {
        mutateOrder(message.orderId(), order -> {
            order.setSagaState("COMPENSATING");
            order.setFailureReason(message.reason());
        });
        eventPublisher.publishSagaEvent(copyWithType(message, SagaEventType.RELEASE_INVENTORY, message.reason()));
    }

    private void handleInventoryReleased(SagaMessage message) {
        SupplyOrder order = mutateOrder(message.orderId(), current -> {
            current.setStatus("CANCELLED");
            current.setSagaState("COMPENSATED");
            current.setFailureReason(message.reason());
        });
        eventPublisher.publishOperationalEvent(order.getId(),
                "ORDER_COMPENSATED id=%d sagaState=%s reason=%s".formatted(
                        order.getId(), order.getSagaState(), order.getFailureReason()));
    }

    private SagaMessage copyWithType(SagaMessage message, SagaEventType nextType, String reason) {
        return new SagaMessage(
                nextType,
                message.sagaId(),
                message.orderId(),
                message.itemName(),
                message.quantity(),
                reason,
                java.time.LocalDateTime.now()
        );
    }

    private SupplyOrder mutateOrder(Long orderId, java.util.function.Consumer<SupplyOrder> mutation) {
        SupplyOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found for saga: " + orderId));
        mutation.accept(order);
        return orderRepository.save(order);
    }
}
