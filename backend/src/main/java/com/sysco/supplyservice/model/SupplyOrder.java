package com.sysco.supplyservice.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "supply_orders")
@Data
public class SupplyOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName;
    private Integer quantity;
    private String status;
}