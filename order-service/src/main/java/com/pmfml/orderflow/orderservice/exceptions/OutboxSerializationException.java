package com.pmfml.orderflow.orderservice.exceptions;

/**
 * Raised when a domain event payload cannot be serialized into the outbox.
 *
 * <p>Genuinely a server fault: the order is valid but the service failed to record
 * its event. Thrown inside the transactional boundary so the order is rolled back
 * rather than persisted without an event, which would leave the Saga unable to
 * start and the order stuck in {@code PENDING} forever.
 */
public class OutboxSerializationException extends RuntimeException {

    public OutboxSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
