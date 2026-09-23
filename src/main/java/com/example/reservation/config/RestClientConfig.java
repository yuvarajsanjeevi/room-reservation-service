package com.example.reservation.config;

import com.example.reservation.exception.UpstreamUnavailableException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    @Bean
    public RestClient creditCardPaymentRestClient(RestClient.Builder builder, CreditCardPaymentProperties properties) {
        log.info("credit-card-payment-service base URL: {}", properties.baseUrl());
        return builder.baseUrl(properties.baseUrl()).build();
    }

    @Bean
    public RetryTemplate creditCardPaymentRetryTemplate(CreditCardPaymentProperties properties) {
        log.info("Retrying credit-card-payment-service failures up to {} time(s), starting at {} and capped at {}",
                properties.maxRetries(), properties.retryDelay(), properties.retryMaxDelay());
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(properties.maxRetries())
                .delay(properties.retryDelay())
                .multiplier(2.0)
                .maxDelay(properties.retryMaxDelay())
                .includes(UpstreamUnavailableException.class, IOException.class)
                .build();
        return new RetryTemplate(policy);
    }
}
