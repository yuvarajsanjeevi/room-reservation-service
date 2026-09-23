package com.example.reservation.client;

import com.example.reservation.client.dto.PaymentStatusResponse;

public interface CreditCardPaymentClient {

    PaymentStatusResponse retrievePaymentStatus(String paymentReference);
}
