package com.sysco.supplyservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests — loads complete Spring context with H2 + EmbeddedKafka.
 * Tests the real flow end-to-end: HTTP → Service → Repository → Kafka.
 * No real Postgres or Kafka broker needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"orders-topic"})
@DirtiesContext
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
    }

    // ── Core CRUD flow ─────────────────────────────────────────────────────

    @Test
    void createOrder_thenFetchById_returnsCorrectData() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Gear X\",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Long id = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class).getId();

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Gear X"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void statusTransition_pendingToProcessingToShipped() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Sprocket B\",\"quantity\":5}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class).getId();

        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    // ── Error handling ─────────────────────────────────────────────────────

    @Test
    void getOrderById_returns404ForNonExistentOrder() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateStatus_returns400ForInvalidStatus() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Valve C\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class).getId();

        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── Actuator ───────────────────────────────────────────────────────────

    @Test
    void actuatorHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
