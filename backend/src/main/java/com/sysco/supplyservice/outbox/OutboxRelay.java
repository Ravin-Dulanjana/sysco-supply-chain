package com.sysco.supplyservice.outbox;

import com.sysco.supplyservice.saga.OrderEventPublisher;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher orderEventPublisher;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, OrderEventPublisher orderEventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:250}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Relaying {} outbox event(s)", pendingEvents.size());
        for (OutboxEvent event : pendingEvents) {
            try {
                orderEventPublisher.publish(event.getTopic(), event.getMessageKey(), event.getPayload());
                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                event.setAttemptCount(event.getAttemptCount() + 1);
                event.setLastError(null);
            } catch (Exception ex) {
                event.setAttemptCount(event.getAttemptCount() + 1);
                event.setLastError(ex.getMessage());
                log.error("Outbox relay failed for event id={} topic={} error={}",
                        event.getId(), event.getTopic(), ex.getMessage());
            }
        }
    }
}
