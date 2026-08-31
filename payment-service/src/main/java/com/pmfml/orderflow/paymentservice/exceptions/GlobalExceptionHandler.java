package com.pmfml.orderflow.paymentservice.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_TYPE_BASE = "https://orderflow.invalid/";
    private static final String TYPE_BAD_REQUEST = "bad-request";
    private static final String TYPE_NOT_FOUND = "payment-not-found";
    private static final String TYPE_INTERNAL_ERROR = "internal-server-error";

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        log.warn("[REST] Payment not found: orderId={}", ex.getOrderId());

        ProblemDetail problemDetail = problem(HttpStatus.NOT_FOUND, TYPE_NOT_FOUND,
                "Payment Not Found", ex.getMessage());
        problemDetail.setProperty("orderId", ex.getOrderId());
        return problemDetail;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingRequestHeader(MissingRequestHeaderException ex) {
        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQUEST,
                "Bad Request", "Required header is missing.");
        problemDetail.setProperty("missingHeader", ex.getHeaderName());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("[REST] Unhandled exception: {}", ex.getMessage(), ex);

        return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL_ERROR,
                "Internal Server Error", "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_TYPE_BASE + type));
        return problemDetail;
    }
}
