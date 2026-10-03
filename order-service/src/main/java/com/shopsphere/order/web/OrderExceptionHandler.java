package com.shopsphere.order.web;

import com.shopsphere.order.service.InvalidOrderException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class OrderExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    ProblemDetail unprocessable(InvalidOrderException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setTitle("Order cannot be placed");
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrent() {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The order was changed concurrently. Reload and retry.");
        pd.setTitle("Concurrent modification");
        return pd;
    }
}
