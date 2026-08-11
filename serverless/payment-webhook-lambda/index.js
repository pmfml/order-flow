const stripe = require('stripe');
const axios = require('axios');

exports.handler = async (event) => {
    console.log("Received webhook event");
    
    const stripeSecret = process.env.STRIPE_WEBHOOK_SECRET;
    const internalApiKey = process.env.INTERNAL_API_KEY;
    
    // In production, this would be an internal DNS record like http://payment-service.orderflow.local
    const paymentServiceUrl = process.env.PAYMENT_SERVICE_URL || 'http://localhost:8093/internal/v1/payment-webhook';

    if (!stripeSecret || !internalApiKey) {
        console.error("Missing required environment variables.");
        return { statusCode: 500, body: JSON.stringify({ error: "Configuration error" }) };
    }

    try {
        const sig = event.headers['Stripe-Signature'] || event.headers['stripe-signature'];
        
        // AWS API Gateway payload handling for signature verification
        let rawBody = event.body;
        if (event.isBase64Encoded) {
            rawBody = Buffer.from(event.body, 'base64').toString('utf8');
        }

        // 1. Verify the Stripe cryptographic signature
        const stripeEvent = stripe.webhooks.constructEvent(rawBody, sig, stripeSecret);
        console.log(`Webhook verified successfully. Event type: ${stripeEvent.type}`);

        // 2. We only care about payments succeeding or failing
        if (stripeEvent.type === 'payment_intent.succeeded' || stripeEvent.type === 'payment_intent.payment_failed') {
            
            const payload = {
                eventType: stripeEvent.type,
                externalReference: stripeEvent.data.object.id,
                orderId: stripeEvent.data.object.metadata.orderId,
                tenantId: stripeEvent.data.object.metadata.tenantId,
                status: stripeEvent.type === 'payment_intent.succeeded' ? 'CAPTURED' : 'FAILED',
                amount: stripeEvent.data.object.amount_received || stripeEvent.data.object.amount
            };

            // 3. Forward securely to the internal Payment Service
            console.log(`Forwarding payload to internal Payment Service...`);
            await axios.post(paymentServiceUrl, payload, {
                headers: {
                    'X-Internal-Api-Key': internalApiKey,
                    'Content-Type': 'application/json'
                }
            });
            console.log("Payload forwarded successfully.");
        } else {
            console.log(`Ignored unhandled event type: ${stripeEvent.type}`);
        }

        return {
            statusCode: 200,
            body: JSON.stringify({ received: true }),
        };

    } catch (error) {
        console.error("Webhook processing failed:", error.message);
        
        // Return 400 so Stripe knows something went wrong with the payload/signature
        return {
            statusCode: 400,
            body: JSON.stringify({ error: `Webhook processing failed: ${error.message}` }),
        };
    }
};
