package com.sysco.supplyservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Smoke test — verifies the full Spring application context starts without errors.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders-topic"})
@DirtiesContext
class SupplyServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
