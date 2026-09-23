package com.example.reservation.service;

import com.example.reservation.dto.ReservationRequest;
import com.example.reservation.entity.Reservation;
import java.math.BigDecimal;

public interface ReservationService {

    /** Confirms a reservation - cash right away, credit card after checking with credit-card-payment-service, bank transfer as pending. */
    Reservation confirmReservation(ReservationRequest request);

    /** Looks up a reservation by id, or throws if it doesn't exist. */
    Reservation getReservation(String reservationId);

    /** Applies a bank transfer payment to a reservation's balance; safe to call twice with the same paymentId. */
    void applyBankTransferPayment(String paymentId, String reservationId, BigDecimal amountReceived);

    /** Cancels bank-transfer reservations that are still unpaid past their lead time. */
    int cancelExpiredBankTransferReservations();
}
