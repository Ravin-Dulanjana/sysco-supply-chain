package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderRequest;
import com.sysco.supplyservice.dto.OrderResponse;
import com.sysco.supplyservice.exception.OrderNotFoundException;
import com.sysco.supplyservice.idempotency.IdempotencyRecord;
import com.sysco.supplyservice.idempotency.IdempotencyRecordRepository;
import com.sysco.supplyservice.model.SupplyOrder;
import com.sysco.supplyservice.outbox.OutboxService;
import com.sysco.supplyservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private OutboxService outboxService;

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

        CreateOrderResult result = orderService.placeOrder(req, null);
        OrderResponse resp = result.order();

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getStatus()).isEqualTo("PENDING");
        assertThat(result.created()).isTrue();
        verify(orderRepository).save(any(SupplyOrder.class));
    }

    @Test
    void placeOrder_publishesKafkaEvent() {
        OrderRequest req = new OrderRequest();
        req.setItemName("Widget A");
        req.setQuantity(10);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        orderService.placeOrder(req, null);

        verify(outboxService).enqueueOperationalEvent(eq(1L), eq("ORDER_PLACED"), contains("ORDER_PLACED"));
        verify(outboxService).enqueueSagaEvent(any());
    }

    @Test
    void placeOrder_returnsExistingOrderForMatchingIdempotencyKey() {
        OrderRequest req = new OrderRequest();
        req.setItemName("Widget A");
        req.setQuantity(10);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey("idem-1");
        record.setRequestHash("de4943cc997c9a52ff3f2405d2f3af1bb61afcc66869792776b5b107024201c9");
        record.setOrderId(1L);

        when(idempotencyRecordRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(record));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(savedOrder));

        CreateOrderResult result = orderService.placeOrder(req, "idem-1");

        assertThat(result.created()).isFalse();
        assertThat(result.order().getId()).isEqualTo(1L);
        verify(orderRepository, never()).save(any(SupplyOrder.class));
        verify(outboxService, never()).enqueueSagaEvent(any());
    }

    @Test
    void placeOrder_throwsWhenIdempotencyKeyReusedWithDifferentPayload() {
        OrderRequest req = new OrderRequest();
        req.setItemName("Other Widget");
        req.setQuantity(10);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey("idem-1");
        record.setRequestHash("de4943cc997c9a52ff3f2405d2f3af1bb61afcc66869792776b5b107024201c9");
        record.setOrderId(1L);

        when(idempotencyRecordRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> orderService.placeOrder(req, "idem-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different request payload");
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
