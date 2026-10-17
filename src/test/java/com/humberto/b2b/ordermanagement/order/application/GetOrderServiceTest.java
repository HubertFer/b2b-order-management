package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetOrderServiceTest {

    @Test
    void returnsExistingOrder() {
        Order order = Order.create(UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00"))),
                Instant.parse("2026-10-15T12:00:00Z"));
        GetOrderService service = new GetOrderService(id -> Optional.of(order));

        assertSame(order, service.getById(order.id()));
    }

    @Test
    void rejectsMissingOrder() {
        GetOrderService service = new GetOrderService(id -> Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }
}
