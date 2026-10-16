package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.DebitPartnerCreditPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.FindOrderForUpdatePort;
import com.humberto.b2b.ordermanagement.order.application.port.out.SaveOrderPort;
import com.humberto.b2b.ordermanagement.order.domain.InvalidOrderTransitionException;
import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import com.humberto.b2b.ordermanagement.partner.domain.InsufficientCreditException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApproveOrderServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant APPROVED_AT = CREATED_AT.plusSeconds(60);

    @Test
    void approvesOrderAfterSuccessfulCreditDebit() {
        UUID partnerId = UUID.randomUUID();
        Order order = createOrder(partnerId);
        List<Order> savedOrders = new ArrayList<>();
        AtomicBoolean debited = new AtomicBoolean();
        ApproveOrderService service = serviceFor(Optional.of(order), savedOrders,
                (id, amount) -> {
                    assertEquals(partnerId, id);
                    assertEquals(new BigDecimal("25.00"), amount);
                    debited.set(true);
                    return true;
                });

        Order approved = service.approve(order.id());

        assertTrue(debited.get());
        assertEquals(OrderStatus.APROVADO, approved.status());
        assertEquals(APPROVED_AT, approved.updatedAt());
        assertEquals(OrderStatus.PENDENTE, order.status());
        assertEquals(List.of(approved), savedOrders);
    }

    @Test
    void rejectsMissingOrderWithoutDebitOrSave() {
        List<Order> savedOrders = new ArrayList<>();
        AtomicBoolean debitAttempted = new AtomicBoolean();
        ApproveOrderService service = serviceFor(Optional.empty(), savedOrders,
                (id, amount) -> {
                    debitAttempted.set(true);
                    return true;
                });

        assertThrows(OrderNotFoundException.class, () -> service.approve(UUID.randomUUID()));

        assertEquals(List.of(), savedOrders);
        assertFalse(debitAttempted.get());
    }

    @Test
    void rejectsInvalidStatusBeforeAttemptingDebit() {
        Order alreadyApproved = createOrder(UUID.randomUUID())
                .transitionTo(OrderStatus.APROVADO, CREATED_AT);
        List<Order> savedOrders = new ArrayList<>();
        AtomicBoolean debitAttempted = new AtomicBoolean();
        ApproveOrderService service = serviceFor(Optional.of(alreadyApproved), savedOrders,
                (id, amount) -> {
                    debitAttempted.set(true);
                    return true;
                });

        assertThrows(InvalidOrderTransitionException.class,
                () -> service.approve(alreadyApproved.id()));

        assertEquals(List.of(), savedOrders);
        assertFalse(debitAttempted.get());
    }

    @Test
    void rejectsInsufficientCreditWithoutSavingOrder() {
        Order order = createOrder(UUID.randomUUID());
        List<Order> savedOrders = new ArrayList<>();
        ApproveOrderService service = serviceFor(Optional.of(order), savedOrders,
                (id, amount) -> false);

        assertThrows(InsufficientCreditException.class, () -> service.approve(order.id()));

        assertEquals(List.of(), savedOrders);
        assertEquals(OrderStatus.PENDENTE, order.status());
    }

    private static ApproveOrderService serviceFor(Optional<Order> order,
                                                   List<Order> savedOrders,
                                                   DebitPartnerCreditPort debitPort) {
        FindOrderForUpdatePort findPort = ignored -> order;
        SaveOrderPort savePort = value -> {
            savedOrders.add(value);
            return value;
        };
        Clock clock = Clock.fixed(APPROVED_AT, ZoneOffset.UTC);
        return new ApproveOrderService(findPort, debitPort, savePort, clock);
    }

    private static Order createOrder(UUID partnerId) {
        return Order.create(partnerId,
                List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("25.00"))),
                CREATED_AT);
    }
}
