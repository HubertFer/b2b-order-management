package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Product, quantity and unit price of an ordered item")
record OrderItemRequest(
        @Schema(description = "Product identifier from the partner's catalog",
                example = "33333333-3333-3333-3333-333333333333", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID productId,

        @Schema(description = "Units ordered, from 1 to 1,000,000",
                example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive @Max(1_000_000) Integer quantity,

        @Schema(description = "Price of one unit, with at most two decimal places",
                example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal unitPrice
) {

    OrderItem toDomain() {
        return new OrderItem(productId, quantity, unitPrice);
    }
}
