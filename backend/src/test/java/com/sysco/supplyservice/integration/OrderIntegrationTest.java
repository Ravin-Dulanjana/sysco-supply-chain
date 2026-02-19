package com.sysco.supplyservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"orders-topic"})
@DirtiesContext
@DisplayName("Full Integration Tests (H2 + EmbeddedKafka)")
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

    @Test
    @DisplayName("POST /api/orders → GET /api/orders/{id}: full create and fetch flow")
    void createOrder_thenFetchById() throws Exception {
        // Create
        OrderRequest req = new OrderRequest();
        req.setItemName("Gear X");
        req.setQuantity(3);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        OrderResponse created = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        Long id = created.getId();

        // Fetch by ID
        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.itemName").value("Gear X"));
    }

    @Test
    @DisplayName("PATCH status flow: PENDING → PROCESSING → SHIPPED")
    void statusTransition_pendingToShipped() throws Exception {
        // Create order
        OrderRequest req = new OrderRequest();
        req.setItemName("Sprocket B");
        req.setQuantity(5);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class).getId();

        // PENDING → PROCESSING
        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        // PROCESSING → SHIPPED
        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("GET /api/orders?status=PENDING: returns only pending orders")
    void filterByStatus_returnsPendingOnly() throws Exception {
        // Create two orders
        for (String item : List.of("A", "B")) {
            OrderRequest req = new OrderRequest();
            req.setItemName(item);
            req.setQuantity(1);
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/orders/{id}: returns 404 for non-existent order")
    void getOrderById_returns404ForMissing() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PATCH status: returns 400 for invalid status value")
    void updateStatus_returns400ForInvalidStatus() throws Exception {
        // Create an order first
        OrderRequest req = new OrderRequest();
        req.setItemName("Valve C");
        req.setQuantity(2);
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class).getId();

        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("FLYING")));
    }

    @Test
    @DisplayName("Actuator /health: returns UP")
    void actuatorHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator /info: returns app info")
    void actuatorInfo_returnsAppInfo() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
