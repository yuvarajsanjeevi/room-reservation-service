package com.example.reservation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "room-reservation.credit-card-payment")
public record CreditCardPaymentProperties(String baseUrl, int maxRetries, Duration retryDelay, Duration retryMaxDelay) {
}
