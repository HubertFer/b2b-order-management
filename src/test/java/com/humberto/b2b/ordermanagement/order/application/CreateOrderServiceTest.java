package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.FindOrderPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.FindPartnerPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.OrderIdempotencyPort;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateOrderServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-15T12:00:00Z");
    private static final String KEY = "order-2026-001";

    private final InMemoryIdempotencyPort idempotencyPort = new InMemoryIdempotencyPort();

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

    @Test
    void createsOrderAndClaimsIdempotencyKey() {
        UUID partnerId = UUID.randomUUID();
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(Partner.create(partnerId, new BigDecimal("100.00"))),
                savedOrders);

        OrderCreation creation = service.create(partnerId, List.of(item(1, "10.00")), KEY);

        assertFalse(creation.replayed());
        assertEquals(List.of(creation.order()), savedOrders);
        assertEquals(creation.order().id(), idempotencyPort.find(partnerId, KEY).orElseThrow().orderId());
    }

    @Test
    void replaysOrderForRepeatedRequest() {
        UUID partnerId = UUID.randomUUID();
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(Partner.create(partnerId, new BigDecimal("100.00"))),
                savedOrders);
        List<OrderItem> items = List.of(item(1, "10.00"));

        OrderCreation first = service.create(partnerId, items, KEY);
        OrderCreation second = service.create(partnerId, items, KEY);

        assertTrue(second.replayed());
        assertSame(first.order(), second.order());
        assertEquals(1, savedOrders.size());
    }

    @Test
    void replaysOrderEvenWhenCreditIsNoLongerAvailable() {
        UUID partnerId = UUID.randomUUID();
        List<Order> savedOrders = new ArrayList<>();
        List<OrderItem> items = List.of(item(1, "10.00"));
        OrderCreation first = serviceFor(Optional.of(Partner.create(partnerId, new BigDecimal("10.00"))), savedOrders)
                .create(partnerId, items, KEY);
        Partner exhausted = new Partner(partnerId, new BigDecimal("10.00"), new BigDecimal("10.00"));

        OrderCreation retry = serviceFor(Optional.of(exhausted), savedOrders).create(partnerId, items, KEY);

        assertTrue(retry.replayed());
        assertSame(first.order(), retry.order());
    }

    @Test
    void rejectsKeyReusedWithDifferentRequest() {
        UUID partnerId = UUID.randomUUID();
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(Partner.create(partnerId, new BigDecimal("100.00"))),
                savedOrders);
        service.create(partnerId, List.of(item(1, "10.00")), KEY);

        assertThrows(IdempotencyKeyReusedException.class,
                () -> service.create(partnerId, List.of(item(2, "10.00")), KEY));
        assertEquals(1, savedOrders.size());
    }

    @Test
    void replaysOrderStoredByConcurrentRequestWhenClaimIsLost() {
        UUID partnerId = UUID.randomUUID();
        List<Order> savedOrders = new ArrayList<>();
        CreateOrderService service = serviceFor(Optional.of(Partner.create(partnerId, new BigDecimal("100.00"))),
                savedOrders);
        List<OrderItem> items = List.of(item(1, "10.00"));
        Order concurrent = Order.create(partnerId, items, CREATED_AT);
        savedOrders.add(concurrent);
        idempotencyPort.claimHook = () -> idempotencyPort.claim(new IdempotencyRecord(partnerId, KEY,
                OrderRequestFingerprint.of(partnerId, items), concurrent.id()));

        OrderCreation creation = service.create(partnerId, items, KEY);

        assertTrue(creation.replayed());
        assertSame(concurrent, creation.order());
        assertEquals(List.of(concurrent), savedOrders);
    }

    private CreateOrderService serviceFor(Optional<Partner> partner, List<Order> savedOrders) {
        FindPartnerPort findPartnerPort = ignored -> partner;
        SaveOrderPort saveOrderPort = order -> {
            savedOrders.add(order);
            return order;
        };
        FindOrderPort findOrderPort = orderId -> savedOrders.stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst();
        Clock clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        return new CreateOrderService(findPartnerPort, saveOrderPort, findOrderPort, idempotencyPort, clock);
    }

    private static class InMemoryIdempotencyPort implements OrderIdempotencyPort {

        private final Map<String, IdempotencyRecord> records = new HashMap<>();
        private Runnable claimHook = () -> {
        };

        @Override
        public Optional<IdempotencyRecord> find(UUID partnerId, String idempotencyKey) {
            return Optional.ofNullable(records.get(partnerId + "|" + idempotencyKey));
        }

        @Override
        public boolean claim(IdempotencyRecord record) {
            Runnable hook = claimHook;
            claimHook = () -> {
            };
            hook.run();
            return records.putIfAbsent(record.partnerId() + "|" + record.idempotencyKey(), record) == null;
        }
    }

    private static OrderItem item(int quantity, String unitPrice) {
        return new OrderItem(UUID.randomUUID(), quantity, new BigDecimal(unitPrice));
    }
}
