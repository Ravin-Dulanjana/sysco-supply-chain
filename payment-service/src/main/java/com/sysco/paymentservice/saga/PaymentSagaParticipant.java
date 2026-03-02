package com.sysco.paymentservice.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PaymentSagaParticipant {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaParticipant.class);

    private final SagaMessageSerializer serializer;
    private final SagaEventPublisher publisher;
    private final String paymentFailureTrigger;

    public PaymentSagaParticipant(
            SagaMessageSerializer serializer,
            SagaEventPublisher publisher,
            @Value("${app.saga.payment-failure-trigger:FAIL_PAYMENT}") String paymentFailureTrigger) {
        this.serializer = serializer;
        this.publisher = publisher;
        this.paymentFailureTrigger = paymentFailureTrigger.toLowerCase(Locale.ROOT);
    }

    @KafkaListener(topics = "${app.saga.topic:order-saga-topic}", groupId = "payment-service-group")
    public void onSagaMessage(String rawMessage) {
        SagaMessage message = serializer.fromJson(rawMessage);
        if (message.eventType() != SagaEventType.REQUEST_PAYMENT) {
            log.debug("Payment service ignoring event={}", message.eventType());
            return;
        }

        String itemName = message.itemName() == null ? "" : message.itemName().toLowerCase(Locale.ROOT);
        if (itemName.contains(paymentFailureTrigger)) {
            log.warn("Payment failed for orderId={} trigger={}", message.orderId(), paymentFailureTrigger);
            publisher.publish(new SagaMessage(
                    SagaEventType.PAYMENT_FAILED,
                    message.sagaId(),
                    message.orderId(),
                    message.itemName(),
                    message.quantity(),
                    "Payment authorization failed",
                    LocalDateTime.now()
            ));
            return;
        }

        log.info("Payment completed for orderId={}", message.orderId());
        publisher.publish(new SagaMessage(
                SagaEventType.PAYMENT_COMPLETED,
                message.sagaId(),
                message.orderId(),
                message.itemName(),
                message.quantity(),
                null,
                LocalDateTime.now()
        ));
    }
}
