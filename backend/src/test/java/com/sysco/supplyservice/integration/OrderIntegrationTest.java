package com.sysco.supplyservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.repository.OrderRepository;
import com.sysco.supplyservice.support.AbstractIntegrationContainers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderIntegrationTest extends AbstractIntegrationContainers {

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
    void createOrder_thenFetchById_returnsCorrectData() throws Exception {
        String body = """
                {"itemName":"Gear X","quantity":3}
                """;
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "create-gear-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readValue(response, OrderResponse.class).getId();

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Gear X"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createOrder_withSameIdempotencyKeyReturnsExistingOrder() throws Exception {
        String body = """
                {"itemName":"Idem Widget","quantity":2}
                """;

        String firstResponse = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "idem-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "idem-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OrderResponse first = objectMapper.readValue(firstResponse, OrderResponse.class);
        OrderResponse second = objectMapper.readValue(secondResponse, OrderResponse.class);

        org.assertj.core.api.Assertions.assertThat(second.getId()).isEqualTo(first.getId());
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void createOrder_completesSagaAndMovesOrderToProcessing() throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "saga-success-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Saga Widget\",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readValue(response, OrderResponse.class).getId();

        awaitOrderState(id, "PROCESSING", "COMPLETED");

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.sagaState").value("COMPLETED"));
    }

    @Test
    void createOrder_withPaymentFailure_compensatesSaga() throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "saga-payment-failure-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"FAIL_PAYMENT Demo\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readValue(response, OrderResponse.class).getId();

        awaitOrderState(id, "CANCELLED", "COMPENSATED");

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaState").value("COMPENSATED"))
                .andExpect(jsonPath("$.failureReason").value("Payment authorization failed"));
    }

    @Test
    void createOrder_withInventoryFailure_cancelsOrder() throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "saga-inventory-failure-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Bulk Demo\",\"quantity\":101}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readValue(response, OrderResponse.class).getId();

        awaitOrderState(id, "CANCELLED", "FAILED");

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaState").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Insufficient inventory for requested quantity"));
    }

    @Test
    void statusTransition_pendingToProcessingToShipped() throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "status-transition-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Sprocket B\",\"quantity\":5}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readValue(response, OrderResponse.class).getId();

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

    @Test
    void getOrderById_returns404ForNonExistentOrder() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateStatus_returns400ForInvalidStatus() throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "invalid-status-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Valve C\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long id = objectMapper.readValue(response, OrderResponse.class).getId();

        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void actuatorHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void awaitOrderState(Long orderId, String expectedStatus, String expectedSagaState) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            OrderResponse order = objectMapper.readValue(
                    mockMvc.perform(get("/api/orders/" + orderId))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString(),
                    OrderResponse.class
            );

            if (expectedStatus.equals(order.getStatus()) && expectedSagaState.equals(order.getSagaState())) {
                return;
            }

            Thread.sleep(250);
        }

        throw new AssertionError("Timed out waiting for order state " + expectedStatus + "/" + expectedSagaState);
    }

    @TestConfiguration
    static class SagaParticipantTestConfig {

        @Bean
        TestInventoryParticipant testInventoryParticipant(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
            return new TestInventoryParticipant(objectMapper, kafkaTemplate);
        }

        @Bean
        TestPaymentParticipant testPaymentParticipant(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
            return new TestPaymentParticipant(objectMapper, kafkaTemplate);
        }
    }

    static class TestInventoryParticipant {

        private final ObjectMapper objectMapper;
        private final KafkaTemplate<String, String> kafkaTemplate;

        TestInventoryParticipant(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
            this.objectMapper = objectMapper;
            this.kafkaTemplate = kafkaTemplate;
        }

        @KafkaListener(topics = "${app.saga.topic:order-saga-topic}", groupId = "test-inventory-participant")
        void onSagaEvent(String rawMessage) throws Exception {
            TestSagaMessage message = objectMapper.readValue(rawMessage, TestSagaMessage.class);
            if ("RESERVE_INVENTORY".equals(message.eventType)) {
                String eventType = message.quantity > 100 ? "INVENTORY_REJECTED" : "INVENTORY_RESERVED";
                String reason = message.quantity > 100 ? "Insufficient inventory for requested quantity" : null;
                kafkaTemplate.send("order-saga-topic", objectMapper.writeValueAsString(
                        message.with(eventType, reason)
                ));
            } else if ("RELEASE_INVENTORY".equals(message.eventType)) {
                kafkaTemplate.send("order-saga-topic", objectMapper.writeValueAsString(
                        message.with("INVENTORY_RELEASED", message.reason)
                ));
            }
        }
    }

    static class TestPaymentParticipant {

        private final ObjectMapper objectMapper;
        private final KafkaTemplate<String, String> kafkaTemplate;

        TestPaymentParticipant(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
            this.objectMapper = objectMapper;
            this.kafkaTemplate = kafkaTemplate;
        }

        @KafkaListener(topics = "${app.saga.topic:order-saga-topic}", groupId = "test-payment-participant")
        void onSagaEvent(String rawMessage) throws Exception {
            TestSagaMessage message = objectMapper.readValue(rawMessage, TestSagaMessage.class);
            if (!"REQUEST_PAYMENT".equals(message.eventType)) {
                return;
            }

            boolean paymentFailure = message.itemName != null && message.itemName.contains("FAIL_PAYMENT");
            String nextEvent = paymentFailure ? "PAYMENT_FAILED" : "PAYMENT_COMPLETED";
            String reason = paymentFailure ? "Payment authorization failed" : null;
            kafkaTemplate.send("order-saga-topic", objectMapper.writeValueAsString(
                    message.with(nextEvent, reason)
            ));
        }
    }

    static class TestSagaMessage {
        public String eventType;
        public String sagaId;
        public Long orderId;
        public String itemName;
        public Integer quantity;
        public String reason;
        public LocalDateTime occurredAt;

        TestSagaMessage with(String nextEventType, String nextReason) {
            TestSagaMessage next = new TestSagaMessage();
            next.eventType = nextEventType;
            next.sagaId = this.sagaId;
            next.orderId = this.orderId;
            next.itemName = this.itemName;
            next.quantity = this.quantity;
            next.reason = nextReason;
            next.occurredAt = LocalDateTime.now();
            return next;
        }
    }
}
