package com.humberto.b2b.ordermanagement.partner.application;

import com.humberto.b2b.ordermanagement.partner.domain.Partner;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreatePartnerServiceTest {

    private final List<Partner> savedPartners = new ArrayList<>();
    private final CreatePartnerService service = new CreatePartnerService(partner -> {
        savedPartners.add(partner);
        return partner;
    });

    @Test
    void createsPartnerWithoutUsedCredit() {
        Partner partner = service.create(new BigDecimal("1000.00"));

        assertNotNull(partner.id());
        assertEquals(new BigDecimal("1000.00"), partner.creditLimit());
        assertEquals(BigDecimal.ZERO, partner.usedCredit());
        assertEquals(List.of(partner), savedPartners);
    }

    @Test
    void generatesDifferentIds() {
        assertNotEquals(service.create(BigDecimal.TEN).id(), service.create(BigDecimal.TEN).id());
    }

    @Test
    void rejectsNegativeCreditLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new BigDecimal("-1.00")));

        assertEquals(List.of(), savedPartners);
    }
}
