package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import com.humberto.b2b.ordermanagement.shared.api.MoneyFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Order with its items, total and current status")
record OrderResponse(
        @Schema(description = "Order identifier", example = "0a3dff20-b5b1-458b-be17-16bdf4436a95")
        UUID id,

        @Schema(description = "Partner that placed the order", example = "22222222-2222-2222-2222-222222222222")
        UUID partnerId,

        @Schema(description = "Ordered items")
        List<OrderItemResponse> items,

        @Schema(description = "Sum of quantity times unit price of all items, with two decimal places", example = "200.00")
        BigDecimal total,

        @Schema(description = "Current status", example = "PENDENTE")
        OrderStatus status,

        @Schema(description = "Creation time in UTC (ISO-8601)", example = "2026-10-17T12:00:00Z")
        Instant createdAt,

        @Schema(description = "Time of the last status change in UTC (ISO-8601)", example = "2026-10-17T12:00:00Z")
        Instant updatedAt
) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.partnerId(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                MoneyFormat.of(order.total()),
                order.status(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
