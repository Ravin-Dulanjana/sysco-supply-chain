package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 * All dependencies are mocked — no Spring context, no DB, no Kafka needed.
 *
 * Note: @Retry (Resilience4j) works via AOP proxy, so it is NOT active here.
 * We test the raw business logic: saving, mapping, throwing, fallback behaviour.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaOperations<String, String> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private SupplyOrder savedOrder;

    @BeforeEach
    void setUp() {
        savedOrder = new SupplyOrder();
        savedOrder.setId(1L);
        savedOrder.setItemName("Widget A");
        savedOrder.setQuantity(10);
        savedOrder.setStatus("PENDING");
    }

    // ── placeOrder ─────────────────────────────────────────────────────────

    @Test
    void placeOrder_savesOrderAndReturnsResponse() {
        OrderRequest req = new OrderRequest();
        req.setItemName("Widget A");
        req.setQuantity(10);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse resp = orderService.placeOrder(req);

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getStatus()).isEqualTo("PENDING");
        verify(orderRepository).save(any(SupplyOrder.class));
    }

    @Test
    void placeOrder_publishesKafkaEvent() {
        OrderRequest req = new OrderRequest();
        req.setItemName("Widget A");
        req.setQuantity(10);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        orderService.placeOrder(req);

        // publishOrderEvent is called directly (no proxy), so Kafka send is invoked
        verify(kafkaTemplate).send(eq("orders-topic"), anyString());
    }

    // ── getOrderById ──────────────────────────────────────────────────────

    @Test
    void getOrderById_returnsResponseWhenFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(savedOrder));

        OrderResponse resp = orderService.getOrderById(1L);

        assertThat(resp.getItemName()).isEqualTo("Widget A");
    }

    @Test
    void getOrderById_throwsOrderNotFoundExceptionWhenMissing() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getAllOrders ───────────────────────────────────────────────────────

    @Test
    void getAllOrders_returnsMappedList() {
        when(orderRepository.findAll()).thenReturn(List.of(savedOrder));

        List<OrderResponse> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemName()).isEqualTo("Widget A");
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────

    @Test
    void updateOrderStatus_updatesStatusSuccessfully() {
        SupplyOrder updated = new SupplyOrder();
        updated.setId(1L);
        updated.setItemName("Widget A");
        updated.setQuantity(10);
        updated.setStatus("SHIPPED");

        when(orderRepository.findById(1L)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any())).thenReturn(updated);

        OrderResponse resp = orderService.updateOrderStatus(1L, "SHIPPED");

        assertThat(resp.getStatus()).isEqualTo("SHIPPED");
    }

    @Test
    void updateOrderStatus_throwsForInvalidStatus() {
        assertThatThrownBy(() -> orderService.updateOrderStatus(1L, "FLYING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLYING");
    }

    @Test
    void updateOrderStatus_throwsWhenOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(99L, "SHIPPED"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── publishFallback ───────────────────────────────────────────────────

    @Test
    void publishFallback_doesNotThrow() {
        // Verifies graceful degradation when all Kafka retries are exhausted
        assertThatCode(() -> orderService.publishFallback(savedOrder, new RuntimeException("Kafka down")))
                .doesNotThrowAnyException();
    }
}
