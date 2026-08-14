package com.pmfml.orderflow.orderservice.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Standardized global exception handling using Spring's ProblemDetail (RFC 7807).
 *
 * <p>Status codes carry meaning here. Business outcomes are reported as 4xx so a
 * client can tell "your request conflicts with stock" from "we broke". Only genuine
 * server faults are 5xx, and those are the only ones logged at ERROR with a stack
 * trace, which keeps the error log a list of things worth investigating.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions
 * keep the status they already imply. A bare {@code @ExceptionHandler(Exception.class)}
 * catch-all would otherwise swallow them, reporting an unknown path, an unsupported
 * method or a malformed JSON body as 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Base for {@code type} URIs. The {@code .invalid} TLD is reserved by RFC 2606
     * and used deliberately: the identifiers must be stable without pointing at
     * documentation that does not exist yet.
     */
    private static final String PROBLEM_TYPE_BASE = "https://orderflow.invalid/";

    private static final String TYPE_BAD_REQUEST = "bad-request";
    private static final String TYPE_NOT_FOUND = "product-not-found";
    private static final String TYPE_ORDER_NOT_FOUND = "order-not-found";
    private static final String TYPE_INSUFFICIENT_STOCK = "insufficient-stock";
    private static final String TYPE_SERVICE_UNAVAILABLE = "inventory-unavailable";
    private static final String TYPE_INTERNAL_ERROR = "internal-server-error";

    // --- Business outcomes -------------------------------------------------

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        // WARN, not ERROR: an unknown product id is the client's mistake, and
        // burying it among server faults would make the error log useless.
        log.warn("[REST] Product not found: productId={}", ex.getProductId());

        ProblemDetail problemDetail = problem(HttpStatus.NOT_FOUND, TYPE_NOT_FOUND,
                "Product Not Found", ex.getMessage());
        problemDetail.setProperty("productId", ex.getProductId());
        return problemDetail;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        log.warn("[REST] Order not found: orderId={}", ex.getOrderId());

        ProblemDetail problemDetail = problem(HttpStatus.NOT_FOUND, TYPE_ORDER_NOT_FOUND,
                "Order Not Found", ex.getMessage());
        problemDetail.setProperty("orderId", ex.getOrderId().toString());
        return problemDetail;
    }

    /**
     * 409 rather than 400: the request was well formed and understood, it just
     * conflicts with the current state of stock.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        log.warn("[REST] Insufficient stock: productId={}, requested={}, available={}",
                ex.getProductId(), ex.getRequestedQuantity(), ex.getAvailableQuantity());

        ProblemDetail problemDetail = problem(HttpStatus.CONFLICT, TYPE_INSUFFICIENT_STOCK,
                "Insufficient Stock", ex.getMessage());
        problemDetail.setProperty("productId", ex.getProductId());
        problemDetail.setProperty("requestedQuantity", ex.getRequestedQuantity());
        problemDetail.setProperty("availableQuantity", ex.getAvailableQuantity());
        return problemDetail;
    }

    /**
     * Kept as a dedicated handler rather than deferring to the inherited
     * {@code ServletRequestBindingException} case, so the response can name the
     * header that was missing.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingRequestHeader(MissingRequestHeaderException ex) {
        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQUEST,
                "Bad Request", "Required header is missing.");
        problemDetail.setProperty("missingHeader", ex.getHeaderName());
        return problemDetail;
    }

    // --- Server faults -----------------------------------------------------

    /**
     * 503 rather than 500: nothing is wrong with the order, a dependency is down,
     * so the client is being told the attempt is worth repeating.
     */
    @ExceptionHandler(InventoryUnavailableException.class)
    public ProblemDetail handleInventoryUnavailable(InventoryUnavailableException ex) {
        log.error("[REST] Inventory Service unavailable", ex);

        return problem(HttpStatus.SERVICE_UNAVAILABLE, TYPE_SERVICE_UNAVAILABLE,
                "Service Unavailable", "The inventory service is temporarily unavailable. Please retry.");
    }

    /**
     * Last resort for anything neither this class nor the parent recognises. The
     * detail is deliberately generic so internal messages never reach the client,
     * but the stack trace is logged so a 500 is never silent.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("[REST] Unhandled exception: {}", ex.getMessage(), ex);

        return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL_ERROR,
                "Internal Server Error", "An unexpected error occurred.");
    }

    // --- Spring MVC exceptions --------------------------------------------

    /**
     * Overridden rather than declared as a separate {@code @ExceptionHandler}:
     * the parent already maps this type, and two handlers for it in one advice
     * would fail at startup as an ambiguous mapping.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQUEST,
                "Bad Request", "Validation failed for one or more fields.");

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        problemDetail.setProperty("fieldErrors", errors);

        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Single logging point for every exception the parent handles, so those
     * responses are no longer emitted silently.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        if (statusCode.is5xxServerError()) {
            log.error("[REST] {} while handling request", statusCode, ex);
        } else {
            log.warn("[REST] {} - {}", statusCode, ex.getMessage());
        }

        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_TYPE_BASE + type));
        return problemDetail;
    }
}
