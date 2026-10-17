package com.humberto.b2b.ordermanagement.partner.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

record CreatePartnerRequest(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal creditLimit
) {
}
