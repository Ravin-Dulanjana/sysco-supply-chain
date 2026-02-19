package com.sysco.supplyservice.repository;

import com.sysco.supplyservice.model.SupplyOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("OrderRepository Slice Tests")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        SupplyOrder pending1 = order("Bolt A", 5, "PENDING");
        SupplyOrder pending2 = order("Bolt B", 3, "PENDING");
        SupplyOrder shipped  = order("Nut C",  10, "SHIPPED");

        orderRepository.saveAll(List.of(pending1, pending2, shipped));
    }

    @Test
    @DisplayName("findByStatus: returns only matching orders")
    void findByStatus_returnsMatchingOrders() {
        List<SupplyOrder> pending = orderRepository.findByStatus("PENDING");
        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(o -> o.getStatus().equals("PENDING"));
    }

    @Test
    @DisplayName("findByStatus: returns empty list for non-existent status")
    void findByStatus_returnsEmptyForUnknownStatus() {
        List<SupplyOrder> result = orderRepository.findByStatus("PROCESSING");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByStatus: counts orders correctly")
    void countByStatus_countsCorrectly() {
        assertThat(orderRepository.countByStatus("PENDING")).isEqualTo(2);
        assertThat(orderRepository.countByStatus("SHIPPED")).isEqualTo(1);
        assertThat(orderRepository.countByStatus("CANCELLED")).isEqualTo(0);
    }

    @Test
    @DisplayName("save: persists entity with auto-generated id and timestamps")
    void save_persistsWithTimestamps() {
        SupplyOrder saved = orderRepository.save(order("Gear D", 7, "PENDING"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findAll: returns total number of orders")
    void findAll_returnsTotalOrders() {
        assertThat(orderRepository.findAll()).hasSize(3);
    }

    private SupplyOrder order(String item, int qty, String status) {
        SupplyOrder o = new SupplyOrder();
        o.setItemName(item);
        o.setQuantity(qty);
        o.setStatus(status);
        return o;
    }
}
