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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    }

    private ResultActions postOrder(String json) throws Exception {
        return mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
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
