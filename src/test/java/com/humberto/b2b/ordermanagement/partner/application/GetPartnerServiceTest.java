package com.humberto.b2b.ordermanagement.partner.application;

import com.humberto.b2b.ordermanagement.partner.domain.Partner;
import com.humberto.b2b.ordermanagement.partner.domain.PartnerNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetPartnerServiceTest {

    @Test
    void returnsExistingPartner() {
        Partner partner = new Partner(UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("40.00"));
        GetPartnerService service = new GetPartnerService(id -> Optional.of(partner));

        assertSame(partner, service.getById(partner.id()));
    }

    @Test
    void rejectsMissingPartner() {
        GetPartnerService service = new GetPartnerService(id -> Optional.empty());

        assertThrows(PartnerNotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }
}
