package com.sysco.paymentservice.saga;

import java.time.LocalDateTime;

public record SagaMessage(
        SagaEventType eventType,
        String sagaId,
        Long orderId,
        String itemName,
        Integer quantity,
        String reason,
        LocalDateTime occurredAt
) {
}
