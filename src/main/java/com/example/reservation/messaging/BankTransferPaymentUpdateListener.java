package com.example.reservation.messaging;

import com.example.reservation.domain.TransactionDescription;
import com.example.reservation.exception.InvalidBankTransferEventException;
import com.example.reservation.service.ReservationService;
import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BankTransferPaymentUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(BankTransferPaymentUpdateListener.class);

    private final ReservationService reservationService;

    public BankTransferPaymentUpdateListener(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @KafkaListener(topics = "bank-transfer-payment-update")
    public void onPaymentUpdate(BankTransferPaymentUpdateEvent event) {
        validate(event);
        log.debug("Received bank-transfer-payment-update: paymentId={}, amountReceived={}",
                event.paymentId(), event.amountReceived());

        TransactionDescription description = TransactionDescription.parse(event.transactionDescription());
        reservationService.applyBankTransferPayment(
                event.paymentId(), description.reservationId(), event.amountReceived());
    }

    private static void validate(BankTransferPaymentUpdateEvent event) {
        if (StringUtils.isBlank(event.paymentId())) {
            throw new InvalidBankTransferEventException("paymentId is missing from the event.");
        }
        BigDecimal amount = event.amountReceived();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBankTransferEventException(
                    "amountReceived for payment '%s' must be positive, but was %s.".formatted(event.paymentId(), amount));
        }
    }
}
