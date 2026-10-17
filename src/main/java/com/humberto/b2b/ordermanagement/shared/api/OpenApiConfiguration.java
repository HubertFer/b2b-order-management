package com.humberto.b2b.ordermanagement.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI orderManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("B2B Order Management API")
                .version("v1")
                .description("B2B order management with partner credit control "
                        + "and status change notifications."));
    }
}
