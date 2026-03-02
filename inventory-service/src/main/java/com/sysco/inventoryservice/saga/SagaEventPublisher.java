package com.sysco.inventoryservice.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

@Component
public class SagaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SagaEventPublisher.class);

    private final KafkaOperations<String, String> kafkaTemplate;
    private final SagaMessageSerializer serializer;
    private final String sagaTopic;

    public SagaEventPublisher(
            KafkaOperations<String, String> kafkaTemplate,
            SagaMessageSerializer serializer,
            @Value("${app.saga.topic:order-saga-topic}") String sagaTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.serializer = serializer;
        this.sagaTopic = sagaTopic;
    }

    public void publish(SagaMessage message) {
        String payload = serializer.toJson(message);
        log.info("Publishing inventory saga event={} orderId={}", message.eventType(), message.orderId());
        kafkaTemplate.send(sagaTopic, message.sagaId(), payload);
    }
}
