package com.example.reservation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.reservation.client.CreditCardPaymentClient;
import com.example.reservation.client.dto.CreditCardPaymentStatus;
import com.example.reservation.client.dto.PaymentStatusResponse;
import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.ReservationIdGenerator;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.dto.ReservationRequest;
import com.example.reservation.entity.ProcessedPayment;
import com.example.reservation.entity.Reservation;
import com.example.reservation.exception.InvalidRequestException;
import com.example.reservation.exception.PaymentRejectedException;
import com.example.reservation.exception.ReservationNotFoundException;
import com.example.reservation.exception.RoomUnavailableException;
import com.example.reservation.repository.ProcessedPaymentRepository;
import com.example.reservation.repository.ReservationRepository;
import com.example.reservation.service.RoomPricingCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 5);
    private static final BigDecimal TOTAL_AMOUNT = BigDecimal.valueOf(480);
    private static final String GENERATED_ID = "R0000001";

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ProcessedPaymentRepository processedPaymentRepository;
    @Mock
    private CreditCardPaymentClient creditCardPaymentClient;
    @Mock
    private RoomPricingCalculator pricingCalculator;
    @Mock
    private ReservationIdGenerator idGenerator;

    private ReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReservationServiceImpl(reservationRepository, processedPaymentRepository,
                creditCardPaymentClient, pricingCalculator, idGenerator);
    }

    @Test
    void cashConfirmsImmediately() {
        givenNoOverlapAndPricedAt(TOTAL_AMOUNT);

        Reservation reservation = service.confirmReservation(reservationRequest(PaymentMode.CASH, null));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verifyNoInteractions(creditCardPaymentClient);
    }

    @Test
    void creditCardConfirmedConfirmsTheReservation() {
        givenNoOverlapAndPricedAt(TOTAL_AMOUNT);
        when(creditCardPaymentClient.retrievePaymentStatus("DL123456789"))
                .thenReturn(new PaymentStatusResponse(OffsetDateTime.now(), CreditCardPaymentStatus.CONFIRMED));

        Reservation reservation = service.confirmReservation(reservationRequest(PaymentMode.CREDIT_CARD, "DL123456789"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void creditCardRejectedThrowsAndNeverPersistsAReservation() {
        givenNoOverlap();
        when(pricingCalculator.totalAmount(any(RoomSegment.class), any(), any())).thenReturn(TOTAL_AMOUNT);
        when(idGenerator.generate()).thenReturn(GENERATED_ID);
        when(creditCardPaymentClient.retrievePaymentStatus("DL123456789"))
                .thenReturn(new PaymentStatusResponse(OffsetDateTime.now(), CreditCardPaymentStatus.REJECTED));

        assertThatThrownBy(() -> service.confirmReservation(reservationRequest(PaymentMode.CREDIT_CARD, "DL123456789")))
                .isInstanceOf(PaymentRejectedException.class);

        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void creditCardWithoutPaymentReferenceIsRejectedBeforeCallingThePaymentService() {
        assertThatThrownBy(() -> service.confirmReservation(reservationRequest(PaymentMode.CREDIT_CARD, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("paymentReference");

        verifyNoInteractions(creditCardPaymentClient);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void bankTransferIsBookedPendingPayment() {
        givenNoOverlapAndPricedAt(TOTAL_AMOUNT);

        Reservation reservation = service.confirmReservation(reservationRequest(PaymentMode.BANK_TRANSFER, null));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getAmountReceived()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void overlappingRoomIsRejectedBeforePricingOrPayment() {
        when(reservationRepository.existsOverlapping("101", START, END)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmReservation(reservationRequest(PaymentMode.CASH, null)))
                .isInstanceOf(RoomUnavailableException.class);

        verifyNoInteractions(pricingCalculator, creditCardPaymentClient);
    }

    @Test
    void anOverlapCaughtByTheDatabaseAtSaveTimeIsAlsoRejected() {
        // The pre-check passed (no overlap seen yet), but a concurrent request won the race and the
        // database's exclusion constraint catches it on the actual insert.
        givenNoOverlap();
        when(pricingCalculator.totalAmount(any(RoomSegment.class), any(), any())).thenReturn(TOTAL_AMOUNT);
        when(idGenerator.generate()).thenReturn(GENERATED_ID);
        when(reservationRepository.saveAndFlush(any(Reservation.class)))
                .thenThrow(new DataIntegrityViolationException("reservations_no_room_overlap"));

        assertThatThrownBy(() -> service.confirmReservation(reservationRequest(PaymentMode.CASH, null)))
                .isInstanceOf(RoomUnavailableException.class);
    }

    @Test
    void staysLongerThanThirtyDaysFailFastBeforeAnyOverlapCheck() {
        ReservationRequest request = new ReservationRequest("Jane Doe", "101", START, START.plusDays(31),
                RoomSegment.SMALL, PaymentMode.CASH, null);

        assertThatThrownBy(() -> service.confirmReservation(request)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(reservationRepository, pricingCalculator);
    }

    @Test
    void getReservationReturnsItWhenPresent() {
        Reservation reservation = Reservation.reserve(GENERATED_ID, "Jane Doe", "101", START, END,
                RoomSegment.SMALL, PaymentMode.CASH, null, TOTAL_AMOUNT);
        when(reservationRepository.findById(GENERATED_ID)).thenReturn(Optional.of(reservation));

        assertThat(service.getReservation(GENERATED_ID)).isSameAs(reservation);
    }

    @Test
    void getReservationThrowsWhenAbsent() {
        when(reservationRepository.findById("UNKNOWN1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReservation("UNKNOWN1")).isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void applyingAFullBankTransferPaymentConfirmsTheReservation() {
        Reservation reservation = Reservation.reserve(GENERATED_ID, "Jane Doe", "101", START, END,
                RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, TOTAL_AMOUNT);
        when(processedPaymentRepository.existsById("PAY-1")).thenReturn(false);
        when(reservationRepository.findById(GENERATED_ID)).thenReturn(Optional.of(reservation));

        service.applyBankTransferPayment("PAY-1", GENERATED_ID, TOTAL_AMOUNT);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);
        verify(processedPaymentRepository).save(any(ProcessedPayment.class));
    }

    @Test
    void aPreviouslyProcessedPaymentIsIgnoredOnRedelivery() {
        when(processedPaymentRepository.existsById("PAY-1")).thenReturn(true);

        service.applyBankTransferPayment("PAY-1", GENERATED_ID, TOTAL_AMOUNT);

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void aPaymentForAnUnknownReservationIsIgnored() {
        when(processedPaymentRepository.existsById("PAY-1")).thenReturn(false);
        when(reservationRepository.findById("UNKNOWN1")).thenReturn(Optional.empty());

        service.applyBankTransferPayment("PAY-1", "UNKNOWN1", TOTAL_AMOUNT);

        verify(reservationRepository, never()).save(any());
        verify(processedPaymentRepository, never()).save(any());
    }

    @Test
    void aPaymentForAnAlreadyConfirmedReservationIsIgnored() {
        Reservation reservation = Reservation.reserve(GENERATED_ID, "Jane Doe", "101", START, END,
                RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, TOTAL_AMOUNT);
        reservation.confirm();
        when(processedPaymentRepository.existsById("PAY-1")).thenReturn(false);
        when(reservationRepository.findById(GENERATED_ID)).thenReturn(Optional.of(reservation));

        service.applyBankTransferPayment("PAY-1", GENERATED_ID, TOTAL_AMOUNT);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelExpiredBankTransferReservationsCancelsOnlyEligibleOnes() {
        LocalDate today = LocalDate.now();
        Reservation dueForCancellation = Reservation.reserve("R0000001", "Jane Doe", "101",
                today.plusDays(1), today.plusDays(4), RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, TOTAL_AMOUNT);
        Reservation alreadyFullyPaid = Reservation.reserve("R0000002", "John Roe", "102",
                today.plusDays(1), today.plusDays(4), RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, TOTAL_AMOUNT);
        alreadyFullyPaid.applyBankTransferPayment(TOTAL_AMOUNT);

        when(reservationRepository.findByStatusAndPaymentModeAndStartDateLessThanEqual(
                eq(ReservationStatus.PENDING_PAYMENT), eq(PaymentMode.BANK_TRANSFER), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(dueForCancellation, alreadyFullyPaid))
                .thenReturn(List.of());

        int cancelled = service.cancelExpiredBankTransferReservations();

        assertThat(cancelled).isEqualTo(1);
        assertThat(dueForCancellation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(alreadyFullyPaid.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository, times(1)).save(dueForCancellation);
    }

    @Test
    void cancelExpiredBankTransferReservationsWorksThroughMultipleBatches() {
        LocalDate today = LocalDate.now();
        Reservation firstBatchReservation = Reservation.reserve("R0000001", "Jane Doe", "101",
                today.plusDays(1), today.plusDays(4), RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, TOTAL_AMOUNT);
        Reservation secondBatchReservation = Reservation.reserve("R0000002", "John Roe", "102",
                today.plusDays(1), today.plusDays(4), RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, TOTAL_AMOUNT);

        // Each call to page 0 returns whatever's still PENDING_PAYMENT - once the first batch is
        // cancelled it no longer matches, so the second call naturally returns the next batch.
        when(reservationRepository.findByStatusAndPaymentModeAndStartDateLessThanEqual(
                eq(ReservationStatus.PENDING_PAYMENT), eq(PaymentMode.BANK_TRANSFER), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(firstBatchReservation))
                .thenReturn(List.of(secondBatchReservation))
                .thenReturn(List.of());

        int cancelled = service.cancelExpiredBankTransferReservations();

        assertThat(cancelled).isEqualTo(2);
        assertThat(firstBatchReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(secondBatchReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository, times(3)).findByStatusAndPaymentModeAndStartDateLessThanEqual(
                eq(ReservationStatus.PENDING_PAYMENT), eq(PaymentMode.BANK_TRANSFER), any(LocalDate.class), any(Pageable.class));
    }

    private void givenNoOverlap() {
        when(reservationRepository.existsOverlapping(anyString(), any(), any())).thenReturn(false);
    }

    private void givenNoOverlapAndPricedAt(BigDecimal amount) {
        givenNoOverlap();
        when(pricingCalculator.totalAmount(any(RoomSegment.class), any(), any())).thenReturn(amount);
        when(idGenerator.generate()).thenReturn(GENERATED_ID);
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ReservationRequest reservationRequest(PaymentMode paymentMode, String paymentReference) {
        return new ReservationRequest("Jane Doe", "101", START, END, RoomSegment.SMALL, paymentMode, paymentReference);
    }
}
