package com.sysco.inventoryservice.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InventorySagaParticipant {

    private static final Logger log = LoggerFactory.getLogger(InventorySagaParticipant.class);

    private final SagaMessageSerializer serializer;
    private final SagaEventPublisher publisher;
    private final int inventoryFailureThreshold;

    public InventorySagaParticipant(
            SagaMessageSerializer serializer,
            SagaEventPublisher publisher,
            @Value("${app.saga.inventory-failure-threshold:100}") int inventoryFailureThreshold) {
        this.serializer = serializer;
        this.publisher = publisher;
        this.inventoryFailureThreshold = inventoryFailureThreshold;
    }

    @KafkaListener(topics = "${app.saga.topic:order-saga-topic}", groupId = "inventory-service-group")
    public void onSagaMessage(String rawMessage) {
        SagaMessage message = serializer.fromJson(rawMessage);
        switch (message.eventType()) {
            case RESERVE_INVENTORY -> reserveInventory(message);
            case RELEASE_INVENTORY -> releaseInventory(message);
            default -> log.debug("Inventory service ignoring event={}", message.eventType());
        }
    }

    private void reserveInventory(SagaMessage message) {
        if (message.quantity() != null && message.quantity() > inventoryFailureThreshold) {
            log.warn("Inventory reservation rejected for orderId={} qty={}", message.orderId(), message.quantity());
            publisher.publish(new SagaMessage(
                    SagaEventType.INVENTORY_REJECTED,
                    message.sagaId(),
                    message.orderId(),
                    message.itemName(),
                    message.quantity(),
                    "Inventory unavailable for quantity " + message.quantity(),
                    LocalDateTime.now()
            ));
            return;
        }

        log.info("Inventory reserved for orderId={}", message.orderId());
        publisher.publish(new SagaMessage(
                SagaEventType.INVENTORY_RESERVED,
                message.sagaId(),
                message.orderId(),
                message.itemName(),
                message.quantity(),
                null,
                LocalDateTime.now()
        ));
    }

    private void releaseInventory(SagaMessage message) {
        log.info("Inventory released for orderId={}", message.orderId());
        publisher.publish(new SagaMessage(
                SagaEventType.INVENTORY_RELEASED,
                message.sagaId(),
                message.orderId(),
                message.itemName(),
                message.quantity(),
                message.reason(),
                LocalDateTime.now()
        ));
    }
}
