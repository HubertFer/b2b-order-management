package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

record ChangeOrderStatusRequest(@NotNull OrderStatus status) {
}
