package com.shopsphere.order.service;

/** The request is well-formed but cannot be fulfilled (unknown product, not enough stock). HTTP 422. */
public class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(String message) { super(message); }
}
