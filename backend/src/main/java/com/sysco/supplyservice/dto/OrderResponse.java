package com.sysco.supplyservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for outgoing order responses.
 * We choose which fields to expose — never return the raw entity.
 */
@Data
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String itemName;
    private Integer quantity;
    private String status;
    private String sagaId;
    private String sagaState;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
