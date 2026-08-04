package com.pmfml.orderflow.paymentservice.gateway;

/**
 * Immutable result returned by a {@link PaymentGateway} call.
 *
 * @param success              whether the authorization succeeded
 * @param providerTransactionId the external provider's transaction id (e.g. Stripe PaymentIntent id)
 * @param failureReason         human-readable reason when {@code success} is false; null otherwise
 */
public record PaymentResult(
        boolean success,
        String providerTransactionId,
        String failureReason
) {

    public static PaymentResult authorized(String providerTransactionId) {
        return new PaymentResult(true, providerTransactionId, null);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(false, null, reason);
    }
}
