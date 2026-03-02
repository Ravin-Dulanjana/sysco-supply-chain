package com.sysco.supplyservice.service;

import com.sysco.supplyservice.dto.OrderResponse;

public record CreateOrderResult(OrderResponse order, boolean created) {
}
