package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.FindOrderForUpdatePort;
import com.humberto.b2b.ordermanagement.order.application.port.out.RefundPartnerCreditPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.SaveOrderPort;
import com.humberto.b2b.ordermanagement.order.domain.InvalidOrderTransitionException;
import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CancelOrderServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant CANCELLED_AT = CREATED_AT.plusSeconds(60);

    private final List<Order> savedOrders = new ArrayList<>();
    private final List<BigDecimal> refunds = new ArrayList<>();
    private final List<OrderStatusChangedEvent> recordedEvents = new ArrayList<>();

    @Test
    void cancelsPendingOrderWithoutRefund() {
        Order order = createOrder();

        Order cancelled = serviceFor(Optional.of(order)).cancel(order.id());

        assertEquals(OrderStatus.CANCELADO, cancelled.status());
        assertEquals(CANCELLED_AT, cancelled.updatedAt());
        assertEquals(List.of(cancelled), savedOrders);
        assertEquals(List.of(), refunds);
        assertEquals(1, recordedEvents.size());
        assertEquals(OrderStatus.PENDENTE, recordedEvents.getFirst().previousStatus());
        assertEquals(OrderStatus.CANCELADO, recordedEvents.getFirst().newStatus());
        assertEquals(CANCELLED_AT, recordedEvents.getFirst().occurredAt());
    }

    @Test
    void refundsCreditWhenCancellingApprovedOrder() {
        Order order = createOrder().transitionTo(OrderStatus.APROVADO, CREATED_AT);

        Order cancelled = serviceFor(Optional.of(order)).cancel(order.id());

        assertEquals(OrderStatus.CANCELADO, cancelled.status());
        assertEquals(List.of(new BigDecimal("25.00")), refunds);
        assertEquals(List.of(cancelled), savedOrders);
        assertEquals(OrderStatus.APROVADO, recordedEvents.getFirst().previousStatus());
    }

    @Test
    void refundsCreditWhenCancellingOrderInProcessing() {
        Order order = createOrder()
                .transitionTo(OrderStatus.APROVADO, CREATED_AT)
                .transitionTo(OrderStatus.EM_PROCESSAMENTO, CREATED_AT);

        serviceFor(Optional.of(order)).cancel(order.id());

        assertEquals(List.of(new BigDecimal("25.00")), refunds);
    }

    @Test
    void rejectsCancellingShippedOrderWithoutRefund() {
        Order order = createOrder()
                .transitionTo(OrderStatus.APROVADO, CREATED_AT)
                .transitionTo(OrderStatus.EM_PROCESSAMENTO, CREATED_AT)
                .transitionTo(OrderStatus.ENVIADO, CREATED_AT);

        assertThrows(InvalidOrderTransitionException.class, () -> serviceFor(Optional.of(order)).cancel(order.id()));

        assertEquals(List.of(), refunds);
        assertEquals(List.of(), savedOrders);
        assertEquals(List.of(), recordedEvents);
    }

    @Test
    void rejectsCancellingAlreadyCancelledOrderWithoutRefund() {
        Order order = createOrder().transitionTo(OrderStatus.CANCELADO, CREATED_AT);

        assertThrows(InvalidOrderTransitionException.class, () -> serviceFor(Optional.of(order)).cancel(order.id()));

        assertEquals(List.of(), refunds);
        assertEquals(List.of(), savedOrders);
        assertEquals(List.of(), recordedEvents);
    }

    @Test
    void rejectsMissingOrder() {
        assertThrows(OrderNotFoundException.class, () -> serviceFor(Optional.empty()).cancel(UUID.randomUUID()));

        assertEquals(List.of(), refunds);
        assertEquals(List.of(), savedOrders);
        assertEquals(List.of(), recordedEvents);
    }

    private CancelOrderService serviceFor(Optional<Order> order) {
        FindOrderForUpdatePort findPort = ignored -> order;
        RefundPartnerCreditPort refundPort = (partnerId, amount) -> {
            assertEquals(order.orElseThrow().partnerId(), partnerId);
            refunds.add(amount);
        };
        SaveOrderPort savePort = value -> {
            savedOrders.add(value);
            return value;
        };
        return new CancelOrderService(findPort, refundPort, savePort, recordedEvents::add,
                Clock.fixed(CANCELLED_AT, ZoneOffset.UTC));
    }

    private static Order createOrder() {
        return Order.create(UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))),
                CREATED_AT);
    }
}
