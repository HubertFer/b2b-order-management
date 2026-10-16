package com.humberto.b2b.ordermanagement.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @Test
    void calculatesTotalFromItems() {
        Order order = Order.create(
                UUID.randomUUID(),
                List.of(
                        new OrderItem(UUID.randomUUID(), 2, new BigDecimal("12.50")),
                        new OrderItem(UUID.randomUUID(), 3, new BigDecimal("0.75"))
                ),
                Instant.parse("2026-10-15T12:00:00Z")
        );

        assertEquals(new BigDecimal("27.25"), order.total());
    }

    @Test
    void createsPendingOrderWithGeneratedIdAndCreationTimestamps() {
        UUID partnerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-15T12:00:00Z");

        Order order = Order.create(
                partnerId,
                List.of(new OrderItem(UUID.randomUUID(), 1, BigDecimal.ZERO)),
                createdAt
        );

        assertNotNull(order.id());
        assertEquals(partnerId, order.partnerId());
        assertEquals(OrderStatus.PENDENTE, order.status());
        assertEquals(createdAt, order.createdAt());
        assertEquals(createdAt, order.updatedAt());
    }

    @Test
    void copiesItemsAndRejectsInvalidOrderValues() {
        UUID partnerId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        List<OrderItem> items = new ArrayList<>(
                List.of(new OrderItem(UUID.randomUUID(), 1, BigDecimal.ONE))
        );
        Order order = Order.create(partnerId, items, createdAt);
        items.clear();

        assertEquals(1, order.items().size());
        assertThrows(UnsupportedOperationException.class, () -> order.items().clear());
        assertThrows(NullPointerException.class,
                () -> Order.create(null, List.of(new OrderItem(UUID.randomUUID(), 1, BigDecimal.ONE)), createdAt));
        assertThrows(NullPointerException.class,
                () -> Order.create(partnerId, List.of(new OrderItem(UUID.randomUUID(), 1, BigDecimal.ONE)), null));
        assertThrows(IllegalArgumentException.class, () -> Order.create(partnerId, List.of(), createdAt));
        assertThrows(NullPointerException.class, () -> Order.create(partnerId, null, createdAt));
    }

    @Test
    void allowsConfiguredTransitionsAndUpdatesTimestampWithoutChangingOriginal() {
        Instant createdAt = Instant.parse("2026-10-15T12:00:00Z");
        Instant changedAt = createdAt.plusSeconds(60);
        List<OrderStatus[]> transitions = List.of(
                new OrderStatus[]{OrderStatus.PENDENTE, OrderStatus.APROVADO},
                new OrderStatus[]{OrderStatus.PENDENTE, OrderStatus.CANCELADO},
                new OrderStatus[]{OrderStatus.APROVADO, OrderStatus.EM_PROCESSAMENTO},
                new OrderStatus[]{OrderStatus.APROVADO, OrderStatus.CANCELADO},
                new OrderStatus[]{OrderStatus.EM_PROCESSAMENTO, OrderStatus.ENVIADO},
                new OrderStatus[]{OrderStatus.EM_PROCESSAMENTO, OrderStatus.CANCELADO},
                new OrderStatus[]{OrderStatus.ENVIADO, OrderStatus.ENTREGUE}
        );

        for (OrderStatus[] transition : transitions) {
            Order original = orderWithStatus(transition[0], createdAt);
            Order updated = original.transitionTo(transition[1], changedAt);

            assertNotSame(original, updated);
            assertEquals(transition[0], original.status());
            assertEquals(createdAt, original.updatedAt());
            assertEquals(transition[1], updated.status());
            assertEquals(createdAt, updated.createdAt());
            assertEquals(changedAt, updated.updatedAt());
        }
    }

    @Test
    void rejectsInvalidTransitionsIncludingTransitionsFromFinalStates() {
        Instant timestamp = Instant.parse("2026-10-15T12:00:00Z");

        assertThrows(InvalidOrderTransitionException.class,
                () -> orderWithStatus(OrderStatus.PENDENTE, timestamp)
                        .transitionTo(OrderStatus.EM_PROCESSAMENTO, timestamp));
        assertThrows(InvalidOrderTransitionException.class,
                () -> orderWithStatus(OrderStatus.APROVADO, timestamp)
                        .transitionTo(OrderStatus.ENVIADO, timestamp));
        assertThrows(InvalidOrderTransitionException.class,
                () -> orderWithStatus(OrderStatus.ENVIADO, timestamp)
                        .transitionTo(OrderStatus.CANCELADO, timestamp));
        assertThrows(InvalidOrderTransitionException.class,
                () -> orderWithStatus(OrderStatus.ENTREGUE, timestamp)
                        .transitionTo(OrderStatus.CANCELADO, timestamp));
        assertThrows(InvalidOrderTransitionException.class,
                () -> orderWithStatus(OrderStatus.CANCELADO, timestamp)
                        .transitionTo(OrderStatus.PENDENTE, timestamp));
    }

    @Test
    void rejectsNullTransitionArguments() {
        Order order = orderWithStatus(OrderStatus.PENDENTE, Instant.parse("2026-10-15T12:00:00Z"));

        assertThrows(NullPointerException.class, () -> order.transitionTo(null, Instant.now()));
        assertThrows(NullPointerException.class, () -> order.transitionTo(OrderStatus.APROVADO, null));
    }

    private static Order orderWithStatus(OrderStatus status, Instant createdAt) {
        Order order = Order.create(
                UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), 1, BigDecimal.ONE)),
                createdAt
        );
        return switch (status) {
            case PENDENTE -> order;
            case APROVADO -> order.transitionTo(OrderStatus.APROVADO, createdAt);
            case EM_PROCESSAMENTO -> order.transitionTo(OrderStatus.APROVADO, createdAt)
                    .transitionTo(OrderStatus.EM_PROCESSAMENTO, createdAt);
            case ENVIADO -> order.transitionTo(OrderStatus.APROVADO, createdAt)
                    .transitionTo(OrderStatus.EM_PROCESSAMENTO, createdAt)
                    .transitionTo(OrderStatus.ENVIADO, createdAt);
            case ENTREGUE -> order.transitionTo(OrderStatus.APROVADO, createdAt)
                    .transitionTo(OrderStatus.EM_PROCESSAMENTO, createdAt)
                    .transitionTo(OrderStatus.ENVIADO, createdAt)
                    .transitionTo(OrderStatus.ENTREGUE, createdAt);
            case CANCELADO -> order.transitionTo(OrderStatus.CANCELADO, createdAt);
        };
    }
}
