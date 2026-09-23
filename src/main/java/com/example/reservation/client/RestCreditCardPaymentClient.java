package com.example.reservation.client;

import com.example.reservation.client.dto.PaymentStatusRequest;
import com.example.reservation.client.dto.PaymentStatusResponse;
import com.example.reservation.exception.UpstreamRejectedException;
import com.example.reservation.exception.UpstreamUnavailableException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class RestCreditCardPaymentClient implements CreditCardPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(RestCreditCardPaymentClient.class);

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    public RestCreditCardPaymentClient(RestClient creditCardPaymentRestClient, RetryTemplate creditCardPaymentRetryTemplate) {
        this.restClient = creditCardPaymentRestClient;
        this.retryTemplate = creditCardPaymentRetryTemplate;
    }

    @Override
    public PaymentStatusResponse retrievePaymentStatus(String paymentReference) {
        log.debug("POST /payment-status for reference {}", paymentReference);
        PaymentStatusResponse response = call(
                "payment status for reference '%s'".formatted(paymentReference),
                () -> restClient.post()
                        .uri("/payment-status")
                        .body(new PaymentStatusRequest(paymentReference))
                        .retrieve()
                        .onStatus(status -> status.value() == 404, (request, httpResponse) -> {
                            throw new UpstreamRejectedException(
                                    "credit-card-payment-service does not recognize payment reference '%s'."
                                            .formatted(paymentReference));
                        })
                        .onStatus(HttpStatusCode::is4xxClientError, (request, httpResponse) -> {
                            throw new UpstreamRejectedException(
                                    "credit-card-payment-service rejected payment reference '%s'."
                                            .formatted(paymentReference));
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (request, httpResponse) -> {
                            throw new UpstreamUnavailableException(
                                    "credit-card-payment-service returned %s.".formatted(httpResponse.getStatusCode()));
                        })
                        .body(PaymentStatusResponse.class));
        log.debug("Payment reference {} is {}", paymentReference, response.status());
        return response;
    }

    private <T> T call(String description, Supplier<T> operation) {
        try {
            return retryTemplate.execute(() -> {
                try {
                    return operation.get();
                } catch (ResourceAccessException e) {
                    log.debug("Could not reach credit-card-payment-service for {}; the retry policy decides next",
                            description, e);
                    throw new UpstreamUnavailableException(
                            "Could not reach credit-card-payment-service for %s.".formatted(description), e);
                }
            });
        } catch (RetryException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new UpstreamUnavailableException(
                    "Could not retrieve %s from credit-card-payment-service.".formatted(description), cause);
        }
    }
}
