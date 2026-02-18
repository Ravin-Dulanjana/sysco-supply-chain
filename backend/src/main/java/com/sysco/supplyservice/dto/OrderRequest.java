package com.sysco.supplyservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for incoming order creation requests.
 * Using a DTO means we control exactly what fields the API accepts,
 * rather than exposing our database entity directly.
 */
@Data
public class OrderRequest {

    @NotBlank(message = "Item name must not be blank")
    private String itemName;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
