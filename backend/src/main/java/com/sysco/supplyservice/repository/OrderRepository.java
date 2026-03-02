package com.sysco.supplyservice.repository;

import com.sysco.supplyservice.model.SupplyOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<SupplyOrder, Long> {

    // Find all orders with a given status (e.g. "PENDING")
    List<SupplyOrder> findByStatus(String status);

    // Count orders by status — useful for dashboards / actuator metrics
    long countByStatus(String status);

    Optional<SupplyOrder> findBySagaId(String sagaId);
}
