package com.humberto.b2b.ordermanagement.partner.domain;

public class InsufficientCreditException extends RuntimeException {

    public InsufficientCreditException() {
        super("Insufficient available credit");
    }
}
