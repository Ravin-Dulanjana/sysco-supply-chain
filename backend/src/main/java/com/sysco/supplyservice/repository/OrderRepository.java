package com.sysco.supplyservice.repository;

import com.sysco.supplyservice.model.SupplyOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<SupplyOrder, Long> {
    // JpaRepository gives us save(), findById(), and delete() automatically!
}