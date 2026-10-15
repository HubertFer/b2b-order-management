package com.humberto.b2b.ordermanagement.order.application;

import com.humberto.b2b.ordermanagement.order.application.port.out.FindPartnerPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.SaveOrderPort;
import com.humberto.b2b.ordermanagement.order.domain.Order;
import com.humberto.b2b.ordermanagement.order.domain.OrderItem;
import com.humberto.b2b.ordermanagement.partner.domain.Partner;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class CreateOrderService {

    private final FindPartnerPort findPartnerPort;
    private final SaveOrderPort saveOrderPort;
    private final Clock clock;

    public CreateOrderService(FindPartnerPort findPartnerPort, SaveOrderPort saveOrderPort, Clock clock) {
        this.findPartnerPort = findPartnerPort;
        this.saveOrderPort = saveOrderPort;
        this.clock = clock;
    }

    public Order create(UUID partnerId, List<OrderItem> items) {
        Partner partner = findPartnerPort.findById(partnerId)
                .orElseThrow(() -> new PartnerNotFoundException(partnerId));
        Order order = Order.create(partnerId, items, clock.instant());
        if (!partner.hasAvailableCredit(order.total())) {
            throw new InsufficientOrderCreditException(partnerId);
        }
        return saveOrderPort.save(order);
    }
}
