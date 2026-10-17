package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OrderRequestFingerprintTest {

    private static final UUID PARTNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    void isStableForSameRequest() {
        assertEquals(fingerprint(PARTNER_ID, 2, "10.00"), fingerprint(PARTNER_ID, 2, "10.00"));
        assertEquals(64, fingerprint(PARTNER_ID, 2, "10.00").length());
    }

    @Test
    void ignoresScaleOfUnitPrice() {
        assertEquals(fingerprint(PARTNER_ID, 2, "10"), fingerprint(PARTNER_ID, 2, "10.00"));
    }

    @Test
    void changesWhenRequestChanges() {
        String original = fingerprint(PARTNER_ID, 2, "10.00");

        assertNotEquals(original, fingerprint(UUID.randomUUID(), 2, "10.00"));
        assertNotEquals(original, fingerprint(PARTNER_ID, 3, "10.00"));
        assertNotEquals(original, fingerprint(PARTNER_ID, 2, "10.01"));
    }

    private static String fingerprint(UUID partnerId, int quantity, String unitPrice) {
        return OrderRequestFingerprint.of(partnerId,
                List.of(new OrderItem(PRODUCT_ID, quantity, new BigDecimal(unitPrice))));
    }
}
