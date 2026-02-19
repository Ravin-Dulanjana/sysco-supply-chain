package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private SupplyOrder sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new SupplyOrder();
        sampleOrder.setId(1L);
        sampleOrder.setItemName("Widget A");
        sampleOrder.setQuantity(10);
        sampleOrder.setStatus("PENDING");
    }

    // ── placeOrder ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: saves order with PENDING status and returns response")
    void placeOrder_savesOrderWithPendingStatus() {
        OrderRequest request = new OrderRequest();
        request.setItemName("Widget A");
        request.setQuantity(10);

        when(orderRepository.save(any(SupplyOrder.class))).thenReturn(sampleOrder);

        OrderResponse response = orderService.placeOrder(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getItemName()).isEqualTo("Widget A");
        assertThat(response.getQuantity()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verify(orderRepository, times(1)).save(any(SupplyOrder.class));
    }

    @Test
    @DisplayName("placeOrder: publishes event to Kafka after saving")
    void placeOrder_publishesKafkaEvent() {
        OrderRequest request = new OrderRequest();
        request.setItemName("Widget B");
        request.setQuantity(5);

        when(orderRepository.save(any(SupplyOrder.class))).thenReturn(sampleOrder);

        orderService.placeOrder(request);

        verify(kafkaTemplate, times(1)).send(eq("orders-topic"), anyString());
    }

    // ── getAllOrders ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllOrders: returns mapped list of all orders")
    void getAllOrders_returnsMappedList() {
        when(orderRepository.findAll()).thenReturn(List.of(sampleOrder));

        List<OrderResponse> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemName()).isEqualTo("Widget A");
    }

    @Test
    @DisplayName("getAllOrders: returns empty list when no orders exist")
    void getAllOrders_returnsEmptyListWhenNone() {
        when(orderRepository.findAll()).thenReturn(List.of());

        List<OrderResponse> result = orderService.getAllOrders();

        assertThat(result).isEmpty();
    }

    // ── getOrderById ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderById: returns order when found")
    void getOrderById_returnsOrderWhenFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        OrderResponse response = orderService.getOrderById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getOrderById: throws OrderNotFoundException when not found")
    void getOrderById_throwsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getOrdersByStatus ─────────────────────────────────────────────────

    @Test
    @DisplayName("getOrdersByStatus: returns filtered list")
    void getOrdersByStatus_returnsFilteredList() {
        when(orderRepository.findByStatus("PENDING")).thenReturn(List.of(sampleOrder));

        List<OrderResponse> result = orderService.getOrdersByStatus("PENDING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getOrdersByStatus: uppercases the status before querying")
    void getOrdersByStatus_uppercasesStatus() {
        when(orderRepository.findByStatus("PENDING")).thenReturn(List.of(sampleOrder));

        orderService.getOrdersByStatus("pending"); // lowercase input

        verify(orderRepository).findByStatus("PENDING");
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────

    @Test
    @DisplayName("updateOrderStatus: updates status and publishes Kafka event")
    void updateOrderStatus_updatesAndPublishes() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        SupplyOrder updated = new SupplyOrder();
        updated.setId(1L);
        updated.setItemName("Widget A");
        updated.setQuantity(10);
        updated.setStatus("PROCESSING");
        when(orderRepository.save(any(SupplyOrder.class))).thenReturn(updated);

        OrderResponse response = orderService.updateOrderStatus(1L, "PROCESSING");

        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        verify(kafkaTemplate, times(1)).send(eq("orders-topic"), anyString());
    }

    @Test
    @DisplayName("updateOrderStatus: throws IllegalArgumentException for invalid status")
    void updateOrderStatus_throwsForInvalidStatus() {
        assertThatThrownBy(() -> orderService.updateOrderStatus(1L, "FLYING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLYING");
    }

    @Test
    @DisplayName("updateOrderStatus: throws OrderNotFoundException when order missing")
    void updateOrderStatus_throwsWhenOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(99L, "SHIPPED"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── Resilience4j fallback ─────────────────────────────────────────────

    @Test
    @DisplayName("publishFallback: logs error without throwing (graceful degradation)")
    void publishFallback_doesNotThrow() {
        assertThatCode(() -> orderService.publishFallback(sampleOrder, new RuntimeException("Kafka down")))
                .doesNotThrowAnyException();
    }
}
