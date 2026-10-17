package com.humberto.b2b.ordermanagement.order.infrastructure;

import com.humberto.b2b.ordermanagement.order.application.ApproveOrderService;
import com.humberto.b2b.ordermanagement.order.application.CreateOrderService;
import com.humberto.b2b.ordermanagement.order.application.port.out.DebitPartnerCreditPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.FindOrderForUpdatePort;
import com.humberto.b2b.ordermanagement.order.application.port.out.FindPartnerPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.SaveOrderPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class OrderUseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CreateOrderService createOrderService(FindPartnerPort findPartnerPort, SaveOrderPort saveOrderPort, Clock clock) {
        return new CreateOrderService(findPartnerPort, saveOrderPort, clock);
    }

    @Bean
    ApproveOrderService approveOrderService(FindOrderForUpdatePort findOrderForUpdatePort,
                                            DebitPartnerCreditPort debitPartnerCreditPort,
                                            SaveOrderPort saveOrderPort,
                                            Clock clock) {
        return new ApproveOrderService(findOrderForUpdatePort, debitPartnerCreditPort, saveOrderPort, clock);
    }
}
