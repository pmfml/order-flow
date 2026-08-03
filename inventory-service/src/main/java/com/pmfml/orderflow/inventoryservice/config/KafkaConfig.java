package com.pmfml.orderflow.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Configures the DefaultErrorHandler with a DeadLetterPublishingRecoverer.
     * <p>
     * If a consumer throws an exception (e.g., OptimisticLockingFailureException), 
     * it will retry 3 times with a 1-second delay.
     * If it still fails, the message is routed to the DLT (topic-name.DLT).
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // Retry 3 times (4 total attempts) with 1000ms backoff
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
