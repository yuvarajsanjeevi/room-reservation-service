package com.example.reservation.config;

import com.example.reservation.exception.InvalidBankTransferEventException;
import com.example.reservation.exception.MalformedTransactionDescriptionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new LoggingRecordRecoverer(), new FixedBackOff(1_000L, 3));
        handler.addNotRetryableExceptions(
                MalformedTransactionDescriptionException.class, InvalidBankTransferEventException.class);
        return handler;
    }
}
