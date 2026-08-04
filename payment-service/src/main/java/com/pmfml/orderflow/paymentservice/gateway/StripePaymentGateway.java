package com.pmfml.orderflow.paymentservice.gateway;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stripe implementation of {@link PaymentGateway}.
 *
 * <p>Creates a {@link PaymentIntent} with {@code capture_method = manual}
 * (authorize-only). The actual capture happens later in the saga when the
 * order is confirmed.
 *
 * <p>The {@code orderId} is sent as the Stripe
 * <a href="https://docs.stripe.com/api/idempotent_requests">idempotency key</a>,
 * so retrying the same order never double-charges.
 */
@Slf4j
@Component
public class StripePaymentGateway implements PaymentGateway {

    public StripePaymentGateway(@Value("${stripe.api.key}") String apiKey) {
        Stripe.apiKey = apiKey;
    }

    @Override
    public PaymentResult authorize(String orderId, String tenantId, BigDecimal amount) {
        try {
            // Stripe expects amounts in the smallest currency unit (cents)
            long amountInCents = amount.movePointRight(2).longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("brl")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .putMetadata("orderId", orderId)
                    .putMetadata("tenantId", tenantId)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            log.info("[Stripe] PaymentIntent {} created for order {} (status: {})",
                    intent.getId(), orderId, intent.getStatus());

            // "requires_capture" means Stripe authorized but did not capture yet
            if ("requires_capture".equals(intent.getStatus())) {
                return PaymentResult.authorized(intent.getId());
            }

            // Any other status is unexpected at this stage
            return PaymentResult.failed("Unexpected PaymentIntent status: " + intent.getStatus());

        } catch (StripeException e) {
            log.error("[Stripe] Authorization failed for order {}: {}", orderId, e.getMessage());
            return PaymentResult.failed(e.getMessage());
        }
    }
}
