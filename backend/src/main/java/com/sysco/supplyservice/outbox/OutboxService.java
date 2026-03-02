package com.sysco.supplyservice.outbox;

import com.sysco.supplyservice.saga.SagaMessage;
import com.sysco.supplyservice.saga.SagaMessageSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private static final String OPERATIONS_TOPIC = "orders-topic";

    private final OutboxEventRepository outboxEventRepository;
    private final SagaMessageSerializer serializer;
    private final String sagaTopic;

    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            SagaMessageSerializer serializer,
            @Value("${app.saga.topic:order-saga-topic}") String sagaTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.serializer = serializer;
        this.sagaTopic = sagaTopic;
    }

    public void enqueueOperationalEvent(Long orderId, String eventType, String payload) {
        enqueue(OPERATIONS_TOPIC, String.valueOf(orderId), eventType, payload);
    }

    public void enqueueSagaEvent(SagaMessage message) {
        enqueue(sagaTopic, message.sagaId(), message.eventType().name(), serializer.toJson(message));
    }

    public void enqueue(String topic, String messageKey, String eventType, String payload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTopic(topic);
        outboxEvent.setMessageKey(messageKey);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(payload);
        outboxEvent.setPublished(false);
        outboxEvent.setAttemptCount(0);
        outboxEvent.setLastError(null);
        outboxEventRepository.save(outboxEvent);
    }
}
