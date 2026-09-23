package com.example.reservation.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.reservation.exception.InvalidBankTransferEventException;
import com.example.reservation.exception.MalformedTransactionDescriptionException;
import com.example.reservation.service.ReservationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankTransferPaymentUpdateListenerTest {

    @Mock
    private ReservationService reservationService;

    private BankTransferPaymentUpdateListener listener;

    @Test
    void parsesTheReservationIdOutOfTheTransactionDescriptionAndAppliesThePayment() {
        listener = new BankTransferPaymentUpdateListener(reservationService);
        BankTransferPaymentUpdateEvent event = new BankTransferPaymentUpdateEvent(
                "PAY-1", "NL91ABNA0417164300", BigDecimal.valueOf(480), "1401541457P4145478");

        listener.onPaymentUpdate(event);

        verify(reservationService).applyBankTransferPayment("PAY-1", "P4145478", BigDecimal.valueOf(480));
    }

    @Test
    void missingPaymentIdIsRejectedBeforeTouchingTheService() {
        listener = new BankTransferPaymentUpdateListener(reservationService);
        BankTransferPaymentUpdateEvent event = new BankTransferPaymentUpdateEvent(
                null, "NL91ABNA0417164300", BigDecimal.valueOf(480), "1401541457P4145478");

        assertThatThrownBy(() -> listener.onPaymentUpdate(event))
                .isInstanceOf(InvalidBankTransferEventException.class);
        verifyNoInteractions(reservationService);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        listener = new BankTransferPaymentUpdateListener(reservationService);
        BankTransferPaymentUpdateEvent event = new BankTransferPaymentUpdateEvent(
                "PAY-1", "NL91ABNA0417164300", BigDecimal.ZERO, "1401541457P4145478");

        assertThatThrownBy(() -> listener.onPaymentUpdate(event))
                .isInstanceOf(InvalidBankTransferEventException.class);
        verifyNoInteractions(reservationService);
    }

    @Test
    void malformedTransactionDescriptionIsRejected() {
        listener = new BankTransferPaymentUpdateListener(reservationService);
        BankTransferPaymentUpdateEvent event = new BankTransferPaymentUpdateEvent(
                "PAY-1", "NL91ABNA0417164300", BigDecimal.valueOf(480), "too-short");

        assertThatThrownBy(() -> listener.onPaymentUpdate(event))
                .isInstanceOf(MalformedTransactionDescriptionException.class);
        verifyNoInteractions(reservationService);
    }
}
