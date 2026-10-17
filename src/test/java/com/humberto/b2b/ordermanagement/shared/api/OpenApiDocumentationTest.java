package com.humberto.b2b.ordermanagement.shared.api;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("unchecked")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsApiEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("B2B Order Management API"))
                .andExpect(jsonPath("$.paths['/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/orders/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/orders/{id}/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/orders/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/orders/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/partners'].post").exists())
                .andExpect(jsonPath("$.paths['/partners/{id}'].get").exists());
    }

    @Test
    void documentsEveryRequestAndResponseField() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Map<String, Object>> schemas = JsonPath.read(apiDocs, "$.components.schemas");

        for (String name : List.of("CreatePartnerRequest", "PartnerResponse", "CreateOrderRequest", "OrderItemRequest",
                "ChangeOrderStatusRequest", "OrderResponse", "OrderItemResponse", "OrderPageResponse")) {
            Map<String, Object> schema = schemas.get(name);
            assertNotNull(schema, name);
            assertNotNull(schema.get("description"), name);
            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) schema.get("properties");
            properties.forEach((property, definition) ->
                    assertNotNull(definition.get("description"), name + "." + property));
        }
    }

    @Test
    void providesExamplesThatWorkAgainstTheComposeEnvironment() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.CreateOrderRequest.properties.partnerId.example")
                        .value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.components.schemas.CreateOrderRequest.properties.items.minItems").value(1))
                .andExpect(jsonPath("$.components.schemas.CreateOrderRequest.properties.items.maxItems").value(100))
                .andExpect(jsonPath("$.components.schemas.CreatePartnerRequest.properties.creditLimit.example")
                        .value(1000.0))
                .andExpect(jsonPath("$.components.schemas.OrderItemRequest.properties.unitPrice.example").value(100.0));
    }

    @Test
    void declaresResponseMediaTypes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/partners'].post.responses['201'].content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/orders'].post.responses['201'].content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/orders'].post.responses['422'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['404'].content['application/problem+json']")
                        .exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
