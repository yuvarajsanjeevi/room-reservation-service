package com.example.reservation.client.dto;

import java.time.OffsetDateTime;

public record PaymentStatusResponse(OffsetDateTime lastUpdateDate, CreditCardPaymentStatus status) {
}
