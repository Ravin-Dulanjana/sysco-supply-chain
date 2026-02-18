package com.sysco.supplyservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO for outgoing order responses.
 * We choose which fields to expose to the client — never expose the raw entity.
 */
@Data
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String itemName;
    private Integer quantity;
    private String status;
}
