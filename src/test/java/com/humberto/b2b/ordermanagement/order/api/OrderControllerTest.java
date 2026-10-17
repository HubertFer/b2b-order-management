package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAndFetchesOrder() throws Exception {
        UUID partnerId = insertPartner("100.00");

        String body = postOrder(orderJson(partnerId, 2, "12.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partnerId").value(partnerId.toString()))
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.5));
    }

    @Test
    void returnsLocationOfCreatedOrder() throws Exception {
        MockHttpServletResponse response = postOrder(orderJson(insertPartner("100.00"), 1, "10.00"))
                .andReturn().getResponse();
        String orderId = JsonPath.read(response.getContentAsString(), "$.id");

        assertTrue(response.getHeader("Location").endsWith("/orders/" + orderId));
    }

    @Test
    void approvesOrderAndDebitsCredit() throws Exception {
        UUID partnerId = insertPartner("100.00");
        String orderId = createOrder(partnerId, 3, "10.00");

        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"));

        assertEquals(new BigDecimal("30.00"), usedCredit(partnerId));
    }

    @Test
    void rejectsSecondApprovalWithConflict() throws Exception {
        String orderId = createOrder(insertPartner("100.00"), 1, "10.00");
        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(get("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/orders/{id}/approve", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOrderForUnknownPartner() throws Exception {
        postOrder(orderJson(UUID.randomUUID(), 1, "10.00"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsOrderAboveAvailableCredit() throws Exception {
        postOrder(orderJson(insertPartner("50.00"), 6, "10.00"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        postOrder("{\"partnerId\": \"%s\", \"items\": []}".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        postOrder(orderJson(UUID.randomUUID(), 0, "10.00"))
                .andExpect(status().isBadRequest());
        postOrder(orderJson(UUID.randomUUID(), 1, "-1.00"))
                .andExpect(status().isBadRequest());
        postOrder(orderJson(UUID.randomUUID(), 1, "10.123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formatsMoneyWithTwoDecimalPlaces() throws Exception {
        String created = postOrder(orderJson(insertPartner("100.00"), 3, "10"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(created, "$.id");
        String fetched = mockMvc.perform(get("/orders/{id}", orderId))
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(created, fetched)) {
            assertTrue(body.contains("\"total\":30.00"), body);
            assertTrue(body.contains("\"unitPrice\":10.00"), body);
        }
    }

    @Test
    void searchesOrdersByStatusAndPeriod() throws Exception {
        Instant before = Instant.now();
        UUID partnerId = insertPartner("100.00");
        String pendingId = createOrder(partnerId, 1, "10.00");
        String approvedId = createOrder(partnerId, 1, "10.00");
        mockMvc.perform(post("/orders/{id}/approve", approvedId))
                .andExpect(status().isOk());
        Instant after = Instant.now().plusSeconds(1);

        mockMvc.perform(get("/orders")
                        .param("status", "APROVADO")
                        .param("createdFrom", before.toString())
                        .param("createdTo", after.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(approvedId)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(pendingId))))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("APROVADO"))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsInvalidSearchParameters() throws Exception {
        mockMvc.perform(get("/orders").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders").param("status", "DESCONHECIDO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders")
                        .param("createdFrom", "2026-10-02T00:00:00Z")
                        .param("createdTo", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movesOrderThroughFulfillmentStatuses() throws Exception {
        String orderId = createOrder(insertPartner("100.00"), 1, "10.00");
        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isOk());

        changeStatus(orderId, "EM_PROCESSAMENTO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PROCESSAMENTO"));
        changeStatus(orderId, "ENVIADO")
                .andExpect(status().isOk());
        changeStatus(orderId, "ENTREGUE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"));
    }

    @Test
    void rejectsInvalidStatusChanges() throws Exception {
        String orderId = createOrder(insertPartner("100.00"), 1, "10.00");

        changeStatus(orderId, "ENVIADO")
                .andExpect(status().isConflict());
        changeStatus(orderId, "APROVADO")
                .andExpect(status().isBadRequest());
        changeStatus(orderId, "CANCELADO")
                .andExpect(status().isBadRequest());
        changeStatus(orderId, "DESCONHECIDO")
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        changeStatus(UUID.randomUUID().toString(), "EM_PROCESSAMENTO")
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelsPendingOrderWithoutTouchingCredit() throws Exception {
        UUID partnerId = insertPartner("100.00");
        String orderId = createOrder(partnerId, 2, "10.00");

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertEquals(new BigDecimal("0.00"), usedCredit(partnerId));
    }

    @Test
    void cancelsApprovedOrderAndRefundsCredit() throws Exception {
        UUID partnerId = insertPartner("100.00");
        String orderId = createOrder(partnerId, 2, "10.00");
        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isOk());
        assertEquals(new BigDecimal("20.00"), usedCredit(partnerId));

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertEquals(new BigDecimal("0.00"), usedCredit(partnerId));
    }

    @Test
    void rejectsCancellingShippedOrder() throws Exception {
        UUID partnerId = insertPartner("100.00");
        String orderId = createOrder(partnerId, 1, "10.00");
        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isOk());
        changeStatus(orderId, "EM_PROCESSAMENTO")
                .andExpect(status().isOk());
        changeStatus(orderId, "ENVIADO")
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict());

        assertEquals(new BigDecimal("10.00"), usedCredit(partnerId));
    }

    @Test
    void returnsNotFoundWhenCancellingUnknownOrder() throws Exception {
        mockMvc.perform(post("/orders/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private ResultActions changeStatus(String orderId, String newStatus) throws Exception {
        return mockMvc.perform(patch("/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"%s\"}".formatted(newStatus)));
    }

    @Test
    void replaysOrderForRepeatedIdempotencyKey() throws Exception {
        UUID partnerId = insertPartner("100.00");
        String json = orderJson(partnerId, 1, "10.00");
        String key = UUID.randomUUID().toString();

        String firstId = JsonPath.read(postOrder(json, key)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        postOrder(json, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));
        assertEquals(1, countOrders(partnerId));
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentRequest() throws Exception {
        UUID partnerId = insertPartner("100.00");
        String key = UUID.randomUUID().toString();
        postOrder(orderJson(partnerId, 1, "10.00"), key)
                .andExpect(status().isCreated());

        postOrder(orderJson(partnerId, 2, "10.00"), key)
                .andExpect(status().isUnprocessableContent());
        assertEquals(1, countOrders(partnerId));
    }

    @Test
    void scopesIdempotencyKeyByPartner() throws Exception {
        String key = UUID.randomUUID().toString();

        postOrder(orderJson(insertPartner("100.00"), 1, "10.00"), key)
                .andExpect(status().isCreated());
        postOrder(orderJson(insertPartner("100.00"), 1, "10.00"), key)
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidIdempotencyKey() throws Exception {
        UUID partnerId = insertPartner("100.00");

        postOrder(orderJson(partnerId, 1, "10.00"), "key with spaces")
                .andExpect(status().isBadRequest());
        postOrder(orderJson(partnerId, 1, "10.00"), "k".repeat(101))
                .andExpect(status().isBadRequest());
        assertEquals(0, countOrders(partnerId));
    }

    private ResultActions postOrder(String json) throws Exception {
        return mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions postOrder(String json, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(json));
    }

    private int countOrders(UUID partnerId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE partner_id = ?",
                Integer.class, partnerId);
    }

    private String createOrder(UUID partnerId, int quantity, String unitPrice) throws Exception {
        String body = postOrder(orderJson(partnerId, quantity, unitPrice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String orderJson(UUID partnerId, int quantity, String unitPrice) {
        return """
                {"partnerId": "%s", "items": [{"productId": "%s", "quantity": %d, "unitPrice": %s}]}
                """.formatted(partnerId, UUID.randomUUID(), quantity, unitPrice);
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
}
