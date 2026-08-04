package com.pmfml.orderflow.paymentservice.gateway;

import java.math.BigDecimal;

/**
 * Abstraction for external payment providers.
 *
 * <p>Isolating Stripe behind an interface lets us:
 * <ul>
 *   <li>Swap providers without touching the domain logic.</li>
 *   <li>Mock the gateway in integration tests (no real Stripe calls).</li>
 * </ul>
 */
public interface PaymentGateway {

    /**
     * Authorizes (but does not capture) the given amount.
     *
     * @param orderId    the order this payment belongs to (used as idempotency key on Stripe)
     * @param tenantId   the tenant requesting the payment
     * @param amount     the amount to authorize
     * @return a {@link PaymentResult} with the provider's transaction id and outcome
     */
    PaymentResult authorize(String orderId, String tenantId, BigDecimal amount);
}
