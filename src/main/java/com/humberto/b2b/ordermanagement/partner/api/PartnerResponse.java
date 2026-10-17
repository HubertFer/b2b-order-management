package com.humberto.b2b.ordermanagement.partner.api;

import com.humberto.b2b.ordermanagement.partner.domain.Partner;

import java.math.BigDecimal;
import java.util.UUID;

record PartnerResponse(UUID id, BigDecimal creditLimit, BigDecimal usedCredit, BigDecimal availableCredit) {

    static PartnerResponse from(Partner partner) {
        return new PartnerResponse(partner.id(), partner.creditLimit(), partner.usedCredit(),
                partner.availableCredit());
    }
}
