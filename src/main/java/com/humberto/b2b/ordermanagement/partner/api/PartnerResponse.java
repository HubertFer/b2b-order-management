package com.humberto.b2b.ordermanagement.partner.api;

import com.humberto.b2b.ordermanagement.partner.domain.Partner;
import com.humberto.b2b.ordermanagement.shared.api.MoneyFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Partner with its credit balance")
record PartnerResponse(
        @Schema(description = "Partner identifier, used as partnerId when creating orders",
                example = "22222222-2222-2222-2222-222222222222")
        UUID id,

        @Schema(description = "Total credit the partner can use", example = "1000.00")
        BigDecimal creditLimit,

        @Schema(description = "Credit consumed by approved orders that were not cancelled", example = "200.00")
        BigDecimal usedCredit,

        @Schema(description = "Credit still available for new approvals (creditLimit - usedCredit)", example = "800.00")
        BigDecimal availableCredit
) {

    static PartnerResponse from(Partner partner) {
        return new PartnerResponse(partner.id(), MoneyFormat.of(partner.creditLimit()),
                MoneyFormat.of(partner.usedCredit()), MoneyFormat.of(partner.availableCredit()));
    }
}
