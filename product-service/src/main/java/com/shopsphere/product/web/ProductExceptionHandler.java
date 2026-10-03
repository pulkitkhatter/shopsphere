package com.shopsphere.product.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ProductExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleUpdate() {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The product was modified by someone else. Reload it and retry.");
        pd.setTitle("Concurrent modification");
        return pd;
    }
}
