package com.humberto.b2b.ordermanagement.order.api;

import com.humberto.b2b.ordermanagement.order.application.InsufficientOrderCreditException;
import com.humberto.b2b.ordermanagement.order.application.OrderNotFoundException;
import com.humberto.b2b.ordermanagement.order.application.PartnerNotFoundException;
import com.humberto.b2b.ordermanagement.order.domain.InvalidOrderTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderController.class)
class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleOrderNotFound(OrderNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({PartnerNotFoundException.class, InsufficientOrderCreditException.class})
    ProblemDetail handleUnprocessable(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidOrderTransitionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
