package com.humberto.b2b.ordermanagement.order.infrastructure.persistence;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.humberto.b2b.ordermanagement.order.application.OrderPage;
import com.humberto.b2b.ordermanagement.order.application.OrderSearchCriteria;
import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrderSearchTest {

    private static final Instant DAY_1 = Instant.parse("2020-01-01T10:00:00Z");
    private static final Instant DAY_2 = Instant.parse("2020-01-02T10:00:00Z");
    private static final Instant DAY_3 = Instant.parse("2020-01-03T10:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2020-01-04T00:00:00Z");

    @Autowired
    private OrderPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private Order pendingDay1;
    private Order approvedDay2;
    private Order pendingDay3;

    @BeforeEach
    void setUp() {
        UUID partnerId = insertPartner();
        pendingDay1 = save(Order.create(partnerId, items(), DAY_1));
        approvedDay2 = save(Order.create(partnerId, items(), DAY_2).transitionTo(OrderStatus.APROVADO, DAY_2));
        pendingDay3 = save(Order.create(partnerId, items(), DAY_3));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void returnsOrdersInPeriodNewestFirst() {
        OrderPage page = adapter.search(new OrderSearchCriteria(null, WINDOW_START, WINDOW_END), 0, 10);

        assertEquals(List.of(pendingDay3.id(), approvedDay2.id(), pendingDay1.id()), ids(page));
        assertEquals(3, page.totalElements());
        assertEquals(1, page.totalPages());
    }

    @Test
    void filtersByStatus() {
        OrderPage page = adapter.search(
                new OrderSearchCriteria(OrderStatus.PENDENTE, WINDOW_START, WINDOW_END), 0, 10);

        assertEquals(List.of(pendingDay3.id(), pendingDay1.id()), ids(page));
    }

    @Test
    void includesStartAndExcludesEndOfPeriod() {
        OrderPage page = adapter.search(new OrderSearchCriteria(null, DAY_1, DAY_3), 0, 10);

        assertEquals(List.of(approvedDay2.id(), pendingDay1.id()), ids(page));
    }

    @Test
    void paginatesResults() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(null, WINDOW_START, WINDOW_END);

        OrderPage first = adapter.search(criteria, 0, 2);
        OrderPage second = adapter.search(criteria, 1, 2);

        assertEquals(List.of(pendingDay3.id(), approvedDay2.id()), ids(first));
        assertEquals(List.of(pendingDay1.id()), ids(second));
        assertEquals(3, second.totalElements());
        assertEquals(2, second.totalPages());
        assertEquals(1, second.page());
        assertEquals(2, second.size());
    }

    @Test
    void loadsItemsOfReturnedOrders() {
        OrderPage page = adapter.search(
                new OrderSearchCriteria(OrderStatus.APROVADO, WINDOW_START, WINDOW_END), 0, 10);

        assertEquals(approvedDay2.items(), page.orders().getFirst().items());
    }

    private Order save(Order order) {
        adapter.save(order);
        return order;
    }

    private static List<OrderItem> items() {
        return List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00")));
    }

    private static List<UUID> ids(OrderPage page) {
        return page.orders().stream().map(Order::id).toList();
    }

    private UUID insertPartner() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO partner (id, credit_limit, used_credit) VALUES (?, 1000.00, 0)", id);
        return id;
    }
}
