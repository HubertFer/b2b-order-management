package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.application.OrderPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One page of orders, newest first")
record OrderPageResponse(
        @Schema(description = "Orders in this page")
        List<OrderResponse> content,

        @Schema(description = "Zero-based page number", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Number of orders matching the filters", example = "1")
        long totalElements,

        @Schema(description = "Number of pages for the requested size", example = "1")
        int totalPages
) {

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
