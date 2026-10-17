package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.SearchOrdersPort;
import com.humberto.b2b.ordermanagement.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchOrdersServiceTest {

    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void delegatesSearchToPort() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(OrderStatus.APROVADO, FROM, TO);
        OrderPage expected = new OrderPage(List.of(), 2, 10, 0, 0);
        SearchOrdersPort port = (receivedCriteria, page, size) -> {
            assertEquals(criteria, receivedCriteria);
            assertEquals(2, page);
            assertEquals(10, size);
            return expected;
        };

        assertSame(expected, new SearchOrdersService(port).search(criteria, 2, 10));
    }

    @Test
    void rejectsInvalidPagination() {
        SearchOrdersService service = new SearchOrdersService((criteria, page, size) -> {
            throw new AssertionError("port must not be called");
        });
        OrderSearchCriteria criteria = new OrderSearchCriteria(null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.search(criteria, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.search(criteria, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(criteria, 0, SearchOrdersService.MAX_PAGE_SIZE + 1));
    }

    @Test
    void rejectsPeriodWhereStartIsNotBeforeEnd() {
        assertThrows(IllegalArgumentException.class, () -> new OrderSearchCriteria(null, TO, FROM));
        assertThrows(IllegalArgumentException.class, () -> new OrderSearchCriteria(null, FROM, FROM));
        assertDoesNotThrow(() -> new OrderSearchCriteria(null, FROM, null));
        assertDoesNotThrow(() -> new OrderSearchCriteria(null, null, TO));
    }
}
