package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.application.OrderPage;

import java.util.List;

record OrderPageResponse(List<OrderResponse> content, int page, int size, long totalElements, int totalPages) {

    static OrderPageResponse from(OrderPage page) {
        return new OrderPageResponse(
                page.orders().stream().map(OrderResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
