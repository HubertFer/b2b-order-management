package com.humberto.b2b.ordermanagement.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rejectsTotalAboveSupportedAmount() {
        UUID partnerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-15T12:00:00Z");
        BigDecimal largestPrice = new BigDecimal("99999999999999999.99");

        assertEquals(largestPrice,
                Order.create(partnerId, List.of(new OrderItem(UUID.randomUUID(), 1, largestPrice)), createdAt).total());
        assertThrows(IllegalArgumentException.class,
                () -> Order.create(partnerId, List.of(new OrderItem(UUID.randomUUID(), 2, largestPrice)), createdAt));
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

    @Test
    void restoresOrderWithAllStoredValues() {
        UUID id = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-15T12:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(120);
        List<OrderItem> items = new ArrayList<>(
                List.of(new OrderItem(UUID.randomUUID(), 2, new BigDecimal("10.00")))
        );

        Order order = Order.restore(id, partnerId, items, new BigDecimal("20.00"),
                OrderStatus.EM_PROCESSAMENTO, createdAt, updatedAt);
        items.clear();

        assertEquals(id, order.id());
        assertEquals(partnerId, order.partnerId());
        assertEquals(1, order.items().size());
        assertEquals(new BigDecimal("20.00"), order.total());
        assertEquals(OrderStatus.EM_PROCESSAMENTO, order.status());
        assertEquals(createdAt, order.createdAt());
        assertEquals(updatedAt, order.updatedAt());
        assertThrows(UnsupportedOperationException.class, () -> order.items().clear());
    }

    @Test
    void rejectsInvalidRestoredValues() {
        UUID id = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        List<OrderItem> items = List.of(new OrderItem(UUID.randomUUID(), 1, BigDecimal.ONE));
        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        assertThrows(NullPointerException.class,
                () -> Order.restore(null, partnerId, items, BigDecimal.ONE, OrderStatus.PENDENTE, now, now));
        assertThrows(NullPointerException.class,
                () -> Order.restore(id, null, items, BigDecimal.ONE, OrderStatus.PENDENTE, now, now));
        assertThrows(NullPointerException.class,
                () -> Order.restore(id, partnerId, null, BigDecimal.ONE, OrderStatus.PENDENTE, now, now));
        assertThrows(NullPointerException.class,
                () -> Order.restore(id, partnerId, items, null, OrderStatus.PENDENTE, now, now));
        assertThrows(NullPointerException.class,
                () -> Order.restore(id, partnerId, items, BigDecimal.ONE, null, now, now));
        assertThrows(NullPointerException.class,
                () -> Order.restore(id, partnerId, items, BigDecimal.ONE, OrderStatus.PENDENTE, null, now));
        assertThrows(NullPointerException.class,
                () -> Order.restore(id, partnerId, items, BigDecimal.ONE, OrderStatus.PENDENTE, now, null));
        assertThrows(IllegalArgumentException.class,
                () -> Order.restore(id, partnerId, List.of(), BigDecimal.ONE, OrderStatus.PENDENTE, now, now));
    }

    @Test
    void reportsWhetherCreditWasDebited() {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        assertFalse(orderWithStatus(OrderStatus.PENDENTE, now).hasDebitedCredit());
        assertTrue(orderWithStatus(OrderStatus.APROVADO, now).hasDebitedCredit());
        assertTrue(orderWithStatus(OrderStatus.EM_PROCESSAMENTO, now).hasDebitedCredit());
        assertTrue(orderWithStatus(OrderStatus.ENVIADO, now).hasDebitedCredit());
        assertTrue(orderWithStatus(OrderStatus.ENTREGUE, now).hasDebitedCredit());
        assertFalse(orderWithStatus(OrderStatus.CANCELADO, now).hasDebitedCredit());
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
