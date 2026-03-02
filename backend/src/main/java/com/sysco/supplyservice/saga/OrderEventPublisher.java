package com.sysco.supplyservice.saga;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String OPERATIONS_TOPIC = "orders-topic";

    private final KafkaOperations<String, String> kafkaTemplate;
    private final SagaMessageSerializer serializer;
    private final String sagaTopic;

    public OrderEventPublisher(
            KafkaOperations<String, String> kafkaTemplate,
            SagaMessageSerializer serializer,
            @Value("${app.saga.topic:order-saga-topic}") String sagaTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.serializer = serializer;
        this.sagaTopic = sagaTopic;
    }

    public void publishOperationalEvent(Long orderId, String message) {
        publish(OPERATIONS_TOPIC, String.valueOf(orderId), message);
    }

    public void publishSagaEvent(SagaMessage message) {
        publish(sagaTopic, message.sagaId(), serializer.toJson(message));
    }

    @Retry(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publish(String topic, String key, String payload) {
        log.info("Publishing to Kafka [{}]: {}", topic, payload);
        kafkaTemplate.send(topic, key, payload);
    }

    public void publishFallback(String topic, String key, String payload, Exception ex) {
        log.error("Kafka publish FAILED after all retries — topic={}, key={}, error={}",
                topic, key, ex.getMessage());
    }
}
