package com.example.reservation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.reservation.client.dto.CreditCardPaymentStatus;
import com.example.reservation.client.dto.PaymentStatusResponse;
import com.example.reservation.exception.UpstreamRejectedException;
import com.example.reservation.exception.UpstreamUnavailableException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestCreditCardPaymentClientTest {

    private static final String URL = "http://credit-card-payment-service/payment-status";

    private MockRestServiceServer mockServer;
    private RestCreditCardPaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://credit-card-payment-service");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(1)
                .delay(Duration.ofMillis(1))
                .includes(UpstreamUnavailableException.class, IOException.class)
                .build());
        client = new RestCreditCardPaymentClient(restClient, retryTemplate);
    }

    @Test
    void confirmedPaymentIsReturned() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.paymentReference").value("DL123456789"))
                .andRespond(withSuccess(
                        "{\"lastUpdateDate\":\"2026-09-20T10:00:00Z\",\"status\":\"CONFIRMED\"}",
                        MediaType.APPLICATION_JSON));

        PaymentStatusResponse response = client.retrievePaymentStatus("DL123456789");

        assertThat(response.status()).isEqualTo(CreditCardPaymentStatus.CONFIRMED);
        mockServer.verify();
    }

    @Test
    void rejectedPaymentIsReturned() {
        mockServer.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"lastUpdateDate\":\"2026-09-20T10:00:00Z\",\"status\":\"REJECTED\"}",
                        MediaType.APPLICATION_JSON));

        PaymentStatusResponse response = client.retrievePaymentStatus("DL999999999");

        assertThat(response.status()).isEqualTo(CreditCardPaymentStatus.REJECTED);
    }

    @Test
    void paymentNotFoundIsRejectedWithoutBeingRetried() {
        mockServer.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.retrievePaymentStatus("UNKNOWN"))
                .isInstanceOf(UpstreamRejectedException.class);

        mockServer.verify();
    }

    @Test
    void serverErrorIsRetriedThenGivesUp() {
        // maxRetries(1) means the initial attempt plus exactly one retry.
        mockServer.expect(times(2), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.retrievePaymentStatus("DL123456789"))
                .isInstanceOf(UpstreamUnavailableException.class);

        mockServer.verify();
    }
}
