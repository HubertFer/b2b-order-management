package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Order to create; it starts as PENDENTE and does not debit credit until approved")
record CreateOrderRequest(
        @Schema(description = "Partner placing the order. The example is a demo partner seeded by Docker Compose; "
                + "use the id returned by POST /partners for your own partners",
                example = "22222222-2222-2222-2222-222222222222", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID partnerId,

        @ArraySchema(arraySchema = @Schema(description = "Ordered items, from 1 to 100",
                requiredMode = Schema.RequiredMode.REQUIRED))
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid OrderItemRequest> items
) {

    List<OrderItem> toDomainItems() {
        return items.stream().map(OrderItemRequest::toDomain).toList();
    }
}
