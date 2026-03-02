package com.sysco.supplyservice.outbox;

import com.sysco.supplyservice.saga.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Test
    void relayPendingEvents_marksEventAsPublishedAfterSuccessfulSend() {
        OutboxEvent event = new OutboxEvent();
        event.setTopic("orders-topic");
        event.setMessageKey("1");
        event.setEventType("ORDER_PLACED");
        event.setPayload("payload");

        when(outboxEventRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        outboxRelay.relayPendingEvents();

        verify(orderEventPublisher).publish("orders-topic", "1", "payload");
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
    }
}
