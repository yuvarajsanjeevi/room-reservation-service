package com.example.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.entity.Reservation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository repository;

    @Test
    void detectsAnOverlappingDateRangeForTheSameRoom() {
        repository.save(reservation("R0000001", "101",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), PaymentMode.CASH, ReservationStatus.CONFIRMED));

        assertThat(repository.existsOverlapping("101", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8))).isTrue();
    }

    @Test
    void backToBackStaysDoNotOverlap() {
        repository.save(reservation("R0000001", "101",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), PaymentMode.CASH, ReservationStatus.CONFIRMED));

        // Checks out the same day the next guest checks in.
        assertThat(repository.existsOverlapping("101", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10))).isFalse();
    }

    @Test
    void aDifferentRoomIsUnaffected() {
        repository.save(reservation("R0000001", "101",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), PaymentMode.CASH, ReservationStatus.CONFIRMED));

        assertThat(repository.existsOverlapping("102", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5))).isFalse();
    }

    @Test
    void aCancelledReservationNoLongerBlocksTheRoom() {
        repository.save(reservation("R0000001", "101",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), PaymentMode.BANK_TRANSFER, ReservationStatus.CANCELLED));

        assertThat(repository.existsOverlapping("101", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5))).isFalse();
    }

    @Test
    void findsOnlyPendingBankTransfersStartingOnOrBeforeTheCutoff() {
        LocalDate today = LocalDate.now();
        Reservation dueSoon = reservation("R0000001", "101", today.plusDays(1), today.plusDays(4),
                PaymentMode.BANK_TRANSFER, ReservationStatus.PENDING_PAYMENT);
        Reservation farOut = reservation("R0000002", "102", today.plusDays(30), today.plusDays(33),
                PaymentMode.BANK_TRANSFER, ReservationStatus.PENDING_PAYMENT);
        Reservation pendingButCash = reservation("R0000003", "103", today.plusDays(1), today.plusDays(4),
                PaymentMode.CASH, ReservationStatus.CONFIRMED);
        repository.saveAll(List.of(dueSoon, farOut, pendingButCash));

        List<Reservation> candidates = repository.findByStatusAndPaymentModeAndStartDateLessThanEqual(
                ReservationStatus.PENDING_PAYMENT, PaymentMode.BANK_TRANSFER, today.plusDays(2), PageRequest.of(0, 50));

        assertThat(candidates).extracting(Reservation::getId).containsExactly("R0000001");
    }

    @Test
    void pageSizeCapsHowManyCandidatesComeBackAtOnce() {
        LocalDate today = LocalDate.now();
        repository.saveAll(List.of(
                reservation("R0000001", "101", today.plusDays(1), today.plusDays(4), PaymentMode.BANK_TRANSFER, ReservationStatus.PENDING_PAYMENT),
                reservation("R0000002", "102", today.plusDays(1), today.plusDays(4), PaymentMode.BANK_TRANSFER, ReservationStatus.PENDING_PAYMENT),
                reservation("R0000003", "103", today.plusDays(1), today.plusDays(4), PaymentMode.BANK_TRANSFER, ReservationStatus.PENDING_PAYMENT)));

        List<Reservation> firstPage = repository.findByStatusAndPaymentModeAndStartDateLessThanEqual(
                ReservationStatus.PENDING_PAYMENT, PaymentMode.BANK_TRANSFER, today.plusDays(2), PageRequest.of(0, 2));

        assertThat(firstPage).hasSize(2);
    }

    private static Reservation reservation(String id, String roomNumber, LocalDate start, LocalDate end,
            PaymentMode paymentMode, ReservationStatus status) {
        Reservation reservation = Reservation.reserve(id, "Jane Doe", roomNumber, start, end,
                RoomSegment.SMALL, paymentMode, null, BigDecimal.valueOf(320));
        if (status == ReservationStatus.CONFIRMED) {
            reservation.confirm();
        } else if (status == ReservationStatus.CANCELLED) {
            reservation.cancel();
        }
        return reservation;
    }
}
