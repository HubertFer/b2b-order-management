package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.humberto.b2b.ordermanagement.order.domain.InvalidOrderTransitionException;
import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApproveOrderConcurrencyTest {

    @Autowired
    private CreateOrderService createOrderService;

    @Autowired
    private ApproveOrderService approveOrderService;

    @Autowired
    private CancelOrderService cancelOrderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentApprovalsNeverExceedPartnerCreditLimit() throws Exception {
        UUID partnerId = insertPartner("100.00");
        List<UUID> orderIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orderIds.add(createOrder(partnerId, "30.00").id());
        }

        List<Throwable> outcomes = approveConcurrently(orderIds);

        assertEquals(3, count(outcomes, null));
        assertEquals(7, count(outcomes, InsufficientOrderCreditException.class));
        assertEquals(new BigDecimal("90.00"), usedCredit(partnerId));
        assertEquals(3, countOrders(partnerId, "APROVADO"));
        assertEquals(7, countOrders(partnerId, "PENDENTE"));
    }

    @Test
    void concurrentApprovalsOfSameOrderDebitOnlyOnce() throws Exception {
        UUID partnerId = insertPartner("100.00");
        UUID orderId = createOrder(partnerId, "30.00").id();

        List<Throwable> outcomes = approveConcurrently(List.of(orderId, orderId, orderId, orderId, orderId));

        assertEquals(1, count(outcomes, null));
        assertEquals(4, count(outcomes, InvalidOrderTransitionException.class));
        assertEquals(new BigDecimal("30.00"), usedCredit(partnerId));
        assertEquals(1, countOrders(partnerId, "APROVADO"));
    }

    @Test
    void concurrentCancellationsOfApprovedOrderRefundOnlyOnce() throws Exception {
        UUID partnerId = insertPartner("100.00");
        UUID orderId = createOrder(partnerId, "30.00").id();
        approveOrderService.approve(orderId);

        List<Throwable> outcomes = runConcurrently(List.of(orderId, orderId, orderId, orderId, orderId),
                cancelOrderService::cancel);

        assertEquals(1, count(outcomes, null));
        assertEquals(4, count(outcomes, InvalidOrderTransitionException.class));
        assertEquals(new BigDecimal("0.00"), usedCredit(partnerId));
        assertEquals(1, countOrders(partnerId, "CANCELADO"));
    }

    @Test
    void concurrentCreationsWithSameIdempotencyKeyCreateOneOrder() throws Exception {
        UUID partnerId = insertPartner("100.00");
        List<OrderItem> items = List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal("10.00")));
        String key = UUID.randomUUID().toString();
        Set<UUID> returnedIds = ConcurrentHashMap.newKeySet();
        List<UUID> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requests.add(UUID.randomUUID());
        }

        List<Throwable> outcomes = runConcurrently(requests, ignored -> {
            Order order = createOrderService.create(partnerId, items, key).order();
            returnedIds.add(order.id());
            return order;
        });

        assertEquals(10, count(outcomes, null));
        assertEquals(1, returnedIds.size());
        assertEquals(1, countOrders(partnerId, "PENDENTE"));
    }

    private List<Throwable> approveConcurrently(List<UUID> orderIds) throws InterruptedException {
        return runConcurrently(orderIds, approveOrderService::approve);
    }

    private List<Throwable> runConcurrently(List<UUID> orderIds, Function<UUID, Order> operation)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(orderIds.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Order>> futures = new ArrayList<>();
            for (UUID orderId : orderIds) {
                Callable<Order> approval = () -> {
                    start.await();
                    return operation.apply(orderId);
                };
                futures.add(executor.submit(approval));
            }
            start.countDown();

            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Order> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    outcomes.add(null);
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                } catch (TimeoutException e) {
                    outcomes.add(e);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static long count(List<Throwable> outcomes, Class<? extends Throwable> type) {
        return outcomes.stream()
                .filter(outcome -> type == null ? outcome == null : type.isInstance(outcome))
                .count();
    }

    private Order createOrder(UUID partnerId, String unitPrice) {
        return createOrderService.create(partnerId,
                List.of(new OrderItem(UUID.randomUUID(), 1, new BigDecimal(unitPrice))));
    }

    private UUID insertPartner(String creditLimit) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO partner (id, credit_limit, used_credit) VALUES (?, ?, 0)",
                id, new BigDecimal(creditLimit));
        return id;
    }

    private BigDecimal usedCredit(UUID partnerId) {
        return jdbcTemplate.queryForObject("SELECT used_credit FROM partner WHERE id = ?",
                BigDecimal.class, partnerId);
    }

    private int countOrders(UUID partnerId, String status) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE partner_id = ? AND status = ?",
                Integer.class, partnerId, status);
    }
}