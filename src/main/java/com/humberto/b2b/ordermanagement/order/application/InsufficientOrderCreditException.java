package com.humberto.b2b.ordermanagement.order.application;

import java.util.UUID;

public class InsufficientOrderCreditException extends RuntimeException {

    public InsufficientOrderCreditException(UUID partnerId) {
        super("Partner " + partnerId + " does not have enough available credit for this order");
    }
}
