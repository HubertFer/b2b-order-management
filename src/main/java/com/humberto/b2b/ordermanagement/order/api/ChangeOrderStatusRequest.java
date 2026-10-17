package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New status for the order")
record ChangeOrderStatusRequest(
        @Schema(description = "Target status. Allowed transitions: PENDENTE -> APROVADO or CANCELADO; "
                + "APROVADO -> EM_PROCESSAMENTO or CANCELADO; EM_PROCESSAMENTO -> ENVIADO or CANCELADO; "
                + "ENVIADO -> ENTREGUE",
                example = "APROVADO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull OrderStatus status
) {
}
