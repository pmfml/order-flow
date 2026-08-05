package com.pmfml.orderflow.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka error-handling configuration for the Payment Service.
 *
 * <p>If a consumer throws an exception (e.g. a transient Stripe timeout),
 * the {@link DefaultErrorHandler} retries up to 3 times with a 1-second
 * backoff. After exhausting retries the message is forwarded to the
 * Dead Letter Topic ({@code <original-topic>.DLT}) for manual inspection,
 * preventing a poison message from blocking the partition.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // 3 retries (4 total attempts), 1000ms between each
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
