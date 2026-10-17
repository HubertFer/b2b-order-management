package com.humberto.b2b.ordermanagement.partner.api;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PartnerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndFetchesPartner() throws Exception {
        MockHttpServletResponse response = createPartner("{\"creditLimit\": 1500.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditLimit").value(1500.0))
                .andExpect(jsonPath("$.usedCredit").value(0))
                .andExpect(jsonPath("$.availableCredit").value(1500.0))
                .andReturn().getResponse();
        String partnerId = JsonPath.read(response.getContentAsString(), "$.id");

        assertTrue(response.getHeader("Location").endsWith("/partners/" + partnerId));
        mockMvc.perform(get("/partners/{id}", partnerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(partnerId))
                .andExpect(jsonPath("$.availableCredit").value(1500.0));
    }

    @Test
    void reflectsDebitAndRefundInCreditBalance() throws Exception {
        String partnerId = idOf(createPartner("{\"creditLimit\": 100.00}"));
        String orderId = idOf(mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"partnerId": "%s", "items": [{"productId": "%s", "quantity": 2, "unitPrice": 15.00}]}
                        """.formatted(partnerId, UUID.randomUUID()))));

        mockMvc.perform(post("/orders/{id}/approve", orderId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/partners/{id}", partnerId))
                .andExpect(jsonPath("$.usedCredit").value(30.0))
                .andExpect(jsonPath("$.availableCredit").value(70.0));

        mockMvc.perform(post("/orders/{id}/cancel", orderId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/partners/{id}", partnerId))
                .andExpect(jsonPath("$.usedCredit").value(0.0))
                .andExpect(jsonPath("$.availableCredit").value(100.0));
    }

    @Test
    void formatsCreditWithTwoDecimalPlaces() throws Exception {
        String created = createPartner("{\"creditLimit\": 100}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fetched = mockMvc.perform(get("/partners/{id}", (String) JsonPath.read(created, "$.id")))
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(created, fetched)) {
            assertTrue(body.contains("\"creditLimit\":100.00"), body);
            assertTrue(body.contains("\"usedCredit\":0.00"), body);
            assertTrue(body.contains("\"availableCredit\":100.00"), body);
        }
    }

    @Test
    void returnsNotFoundForUnknownPartner() throws Exception {
        mockMvc.perform(get("/partners/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidCreditLimit() throws Exception {
        createPartner("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("creditLimit"));
        createPartner("{\"creditLimit\": -1.00}")
                .andExpect(status().isBadRequest());
        createPartner("{\"creditLimit\": 10.123}")
                .andExpect(status().isBadRequest());
    }

    private ResultActions createPartner(String json) throws Exception {
        return mockMvc.perform(post("/partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }
}
