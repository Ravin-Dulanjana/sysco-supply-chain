package com.sysco.supplyservice.exception;

/**
 * Thrown when an order with a given ID cannot be found in the database.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Order not found with id: " + id);
    }
}
