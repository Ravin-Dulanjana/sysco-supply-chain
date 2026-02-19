package com.sysco.supplyservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity mapped to the "supply_orders" table.
 *
 * Status lifecycle:  PENDING → PROCESSING → SHIPPED
 */
@Entity
@Table(name = "supply_orders")
@Data
public class SupplyOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status; // PENDING | PROCESSING | SHIPPED

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
