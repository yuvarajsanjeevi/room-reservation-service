package com.example.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 5);

    @Test
    void reserveStartsPendingPaymentWithZeroReceived() {
        Reservation reservation = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getAmountReceived()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    void endDateMustBeAfterStartDate() {
        assertThatThrownBy(() -> Reservation.reserve("R0000001", "Jane Doe", "101",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1),
                RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.TEN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must be after");
    }

    @Test
    void staysOfExactlyThirtyDaysAreAllowed() {
        Reservation reservation = Reservation.reserve("R0000001", "Jane Doe", "101",
                START, START.plusDays(30), RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.TEN);

        assertThat(reservation.getEndDate()).isEqualTo(START.plusDays(30));
    }

    @Test
    void staysLongerThanThirtyDaysAreRejected() {
        assertThatThrownBy(() -> Reservation.reserve("R0000001", "Jane Doe", "101",
                START, START.plusDays(31), RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.TEN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("30 days");
    }

    @Test
    void partialBankTransferPaymentStaysPending() {
        Reservation reservation = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));

        boolean confirmed = reservation.applyBankTransferPayment(BigDecimal.valueOf(150));

        assertThat(confirmed).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getAmountReceived()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }

    @Test
    void bankTransferPaymentReachingTheTotalConfirms() {
        Reservation reservation = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));
        reservation.applyBankTransferPayment(BigDecimal.valueOf(150));

        boolean confirmed = reservation.applyBankTransferPayment(BigDecimal.valueOf(250));

        assertThat(confirmed).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void overpaymentStillConfirms() {
        Reservation reservation = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));

        boolean confirmed = reservation.applyBankTransferPayment(BigDecimal.valueOf(500));

        assertThat(confirmed).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void paymentAppliedToAnAlreadyConfirmedReservationDoesNotReconfirm() {
        Reservation reservation = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));
        reservation.applyBankTransferPayment(BigDecimal.valueOf(400));

        boolean confirmedAgain = reservation.applyBankTransferPayment(BigDecimal.TEN);

        assertThat(confirmedAgain).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void eligibleForAutoCancellationOnlyWhenPendingBankTransferWithinLeadTime() {
        Reservation pendingBankTransfer = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));
        Reservation pendingCreditCardNeverHappens = Reservation.reserve("R0000002", "Jane Doe", "102",
                START, END, RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.TEN);

        LocalDate wellBeforeCutoff = START.minusDays(10);
        LocalDate atCutoff = START.minusDays(2);

        assertThat(pendingBankTransfer.isEligibleForAutoCancellation(wellBeforeCutoff)).isFalse();
        assertThat(pendingBankTransfer.isEligibleForAutoCancellation(atCutoff)).isTrue();
        assertThat(pendingCreditCardNeverHappens.isEligibleForAutoCancellation(atCutoff)).isFalse();
    }

    @Test
    void fullyPaidReservationIsNotEligibleForAutoCancellationEvenPastTheCutoff() {
        Reservation reservation = reserve(PaymentMode.BANK_TRANSFER, BigDecimal.valueOf(400));
        reservation.applyBankTransferPayment(BigDecimal.valueOf(400));

        assertThat(reservation.isEligibleForAutoCancellation(START.plusDays(1))).isFalse();
    }

    private static Reservation reserve(PaymentMode paymentMode, BigDecimal totalAmount) {
        return Reservation.reserve("R0000001", "Jane Doe", "101", START, END,
                RoomSegment.SMALL, paymentMode, paymentMode == PaymentMode.CREDIT_CARD ? "DL123456789" : null, totalAmount);
    }
}
