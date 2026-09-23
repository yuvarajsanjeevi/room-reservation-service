package com.example.reservation.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BankTransferPaymentUpdateEvent(
        String paymentId,
        @JsonProperty("debtorAccountnumber") String debtorAccountNumber,
        BigDecimal amountReceived,
        String transactionDescription) {
}
