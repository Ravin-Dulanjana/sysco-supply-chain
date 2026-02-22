package com.sysco.supplyservice.repository;

import com.sysco.supplyservice.model.SupplyOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests — only JPA layer is loaded, H2 in-memory DB is used.
 * Faster than full @SpringBootTest, no Kafka or web layer involved.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        orderRepository.saveAll(List.of(
            order("Bolt A", 5, "PENDING"),
            order("Bolt B", 3, "PENDING"),
            order("Nut C", 10, "SHIPPED")
        ));
    }

    @Test
    void findByStatus_returnsOnlyMatchingOrders() {
        List<SupplyOrder> pending = orderRepository.findByStatus("PENDING");
        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(o -> "PENDING".equals(o.getStatus()));
    }

    @Test
    void findByStatus_returnsEmptyListForUnknownStatus() {
        assertThat(orderRepository.findByStatus("PROCESSING")).isEmpty();
    }

    @Test
    void countByStatus_returnsCorrectCounts() {
        assertThat(orderRepository.countByStatus("PENDING")).isEqualTo(2);
        assertThat(orderRepository.countByStatus("SHIPPED")).isEqualTo(1);
        assertThat(orderRepository.countByStatus("CANCELLED")).isEqualTo(0);
    }

    @Test
    void save_persistsTimestampsAutomatically() {
        SupplyOrder saved = orderRepository.save(order("Gear D", 7, "PENDING"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private SupplyOrder order(String item, int qty, String status) {
        SupplyOrder o = new SupplyOrder();
        o.setItemName(item);
        o.setQuantity(qty);
        o.setStatus(status);
        return o;
    }
}
