package com.humberto.b2b.ordermanagement.shared.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyFormatTest {

    @Test
    void usesTwoDecimalPlaces() {
        assertEquals("0.00", MoneyFormat.of(BigDecimal.ZERO).toPlainString());
        assertEquals("10.00", MoneyFormat.of(new BigDecimal("10")).toPlainString());
        assertEquals("10.50", MoneyFormat.of(new BigDecimal("10.5")).toPlainString());
        assertEquals("10.25", MoneyFormat.of(new BigDecimal("10.25")).toPlainString());
    }

    @Test
    void refusesToRoundAmounts() {
        assertThrows(ArithmeticException.class, () -> MoneyFormat.of(new BigDecimal("10.123")));
    }
}
