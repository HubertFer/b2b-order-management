package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.FindOrderForUpdatePort;
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

class ChangeOrderStatusServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant CHANGED_AT = CREATED_AT.plusSeconds(60);

    private final List<Order> savedOrders = new ArrayList<>();
    private final List<OrderStatusChangedEvent> recordedEvents = new ArrayList<>();

    @Test
    void movesApprovedOrderToProcessing() {
        Order order = createOrder().transitionTo(OrderStatus.APROVADO, CREATED_AT);

        Order changed = serviceFor(Optional.of(order)).changeStatus(order.id(), OrderStatus.EM_PROCESSAMENTO);

        assertEquals(OrderStatus.EM_PROCESSAMENTO, changed.status());
        assertEquals(CHANGED_AT, changed.updatedAt());
        assertEquals(List.of(changed), savedOrders);
        assertEquals(1, recordedEvents.size());
        assertEquals(OrderStatus.APROVADO, recordedEvents.getFirst().previousStatus());
        assertEquals(OrderStatus.EM_PROCESSAMENTO, recordedEvents.getFirst().newStatus());
    }

    @Test
    void rejectsTransitionNotAllowedByOrder() {
        Order order = createOrder();

        assertThrows(InvalidOrderTransitionException.class,
                () -> serviceFor(Optional.of(order)).changeStatus(order.id(), OrderStatus.ENVIADO));

        assertEquals(List.of(), savedOrders);
        assertEquals(List.of(), recordedEvents);
    }

    @Test
    void rejectsStatusesThatAffectPartnerCredit() {
        Order order = createOrder();
        ChangeOrderStatusService service = serviceFor(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(order.id(), OrderStatus.APROVADO));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(order.id(), OrderStatus.CANCELADO));

        assertEquals(List.of(), savedOrders);
        assertEquals(List.of(), recordedEvents);
    }

    @Test
    void rejectsMissingOrder() {
        assertThrows(OrderNotFoundException.class,
                () -> serviceFor(Optional.empty()).changeStatus(UUID.randomUUID(), OrderStatus.EM_PROCESSAMENTO));

        assertEquals(List.of(), savedOrders);
        assertEquals(List.of(), recordedEvents);
    }

    private ChangeOrderStatusService serviceFor(Optional<Order> order) {
        FindOrderForUpdatePort findPort = ignored -> order;
        SaveOrderPort savePort = value -> {
            savedOrders.add(value);
            return value;
        };
        return new ChangeOrderStatusService(findPort, savePort, recordedEvents::add, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));
    }

    private static Order createOrder() {
        return Order.create(UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))),
                CREATED_AT);
    }
}
