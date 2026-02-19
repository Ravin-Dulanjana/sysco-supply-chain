package com.sysco.supplyservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders-topic"})
@DirtiesContext
@DisplayName("Application Context Loads")
class SupplyServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the entire Spring context starts without errors
    }
}
