package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import com.humberto.b2b.ordermanagement.shared.api.MoneyFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Ordered item")
record OrderItemResponse(
        @Schema(description = "Product identifier", example = "33333333-3333-3333-3333-333333333333")
        UUID productId,

        @Schema(description = "Units ordered", example = "2")
        int quantity,

        @Schema(description = "Price of one unit, with two decimal places", example = "100.00")
        BigDecimal unitPrice
) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.productId(), item.quantity(), MoneyFormat.of(item.unitPrice()));
    }
}
