package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.FindPartnerPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.SaveOrderPort;
import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import com.humberto.b2b.ordermanagement.partner.domain.Partner;
import com.humberto.b2b.ordermanagement.partner.domain.PartnerNotFoundException;
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

class CreateOrderServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void createsAndSavesPendingOrderWithoutDebitingPartnerCredit() {
        UUID partnerId = UUID.randomUUID();
        Partner partner = Partner.create(partnerId, new BigDecimal("100.00"));
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(partner), savedOrders);

        Order order = service.create(partnerId, List.of(item(2, "15.00")));

        assertEquals(OrderStatus.PENDENTE, order.status());
        assertEquals(new BigDecimal("30.00"), order.total());
        assertEquals(CREATED_AT, order.createdAt());
        assertEquals(List.of(order), savedOrders);
        assertEquals(BigDecimal.ZERO, partner.usedCredit());
    }

    @Test
    void doesNotSaveWhenPartnerDoesNotExist() {
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.empty(), savedOrders);

        assertThrows(PartnerNotFoundException.class,
                () -> service.create(UUID.randomUUID(), List.of(item(1, "10.00"))));
        assertEquals(List.of(), savedOrders);
    }

    @Test
    void doesNotSaveWhenCreditIsInsufficient() {
        UUID partnerId = UUID.randomUUID();
        Partner partner = Partner.create(partnerId, new BigDecimal("10.00"));
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(partner), savedOrders);

        assertThrows(InsufficientOrderCreditException.class,
                () -> service.create(partnerId, List.of(item(2, "10.00"))));
        assertEquals(List.of(), savedOrders);
        assertEquals(BigDecimal.ZERO, partner.usedCredit());
    }

    @Test
    void doesNotSaveAnInvalidOrder() {
        UUID partnerId = UUID.randomUUID();
        Partner partner = Partner.create(partnerId, new BigDecimal("100.00"));
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(partner), savedOrders);

        assertThrows(IllegalArgumentException.class, () -> service.create(partnerId, List.of()));
        assertEquals(List.of(), savedOrders);
    }

    private static CreateOrderService serviceFor(Optional<Partner> partner, List<Order> savedOrders) {
        FindPartnerPort findPartnerPort = ignored -> partner;
        SaveOrderPort saveOrderPort = order -> {
            savedOrders.add(order);
            return order;
        };
        Clock clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        return new CreateOrderService(findPartnerPort, saveOrderPort, clock);
    }

    private static OrderItem item(int quantity, String unitPrice) {
        return new OrderItem(UUID.randomUUID(), quantity, new BigDecimal(unitPrice));
    }
}
