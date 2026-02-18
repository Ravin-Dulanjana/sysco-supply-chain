package com.sysco.supplyservice.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer — listens to the "orders-topic" and simulates warehouse processing.
 *
 * Uses SLF4J logger instead of System.out.println so messages:
 *  - Include timestamps and log levels (INFO, ERROR, etc.)
 *  - Are written to the log file (logs/supply-service.log)
 *  - Can be filtered/searched in production monitoring tools
 */
@Service
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    @KafkaListener(topics = "orders-topic", groupId = "warehouse-group")
    public void consumeOrder(String message) {
        log.info("================================================");
        log.info("WAREHOUSE: Received Kafka message");
        log.info("Message: {}", message);
        log.info("Action: Preparing item for shipment...");
        log.info("================================================");
    }
}
