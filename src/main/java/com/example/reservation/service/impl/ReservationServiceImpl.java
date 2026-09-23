package com.example.reservation.service.impl;

import com.example.reservation.client.CreditCardPaymentClient;
import com.example.reservation.client.dto.CreditCardPaymentStatus;
import com.example.reservation.client.dto.PaymentStatusResponse;
import com.example.reservation.constant.ApplicationConstants;
import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.ReservationIdGenerator;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.dto.ReservationRequest;
import com.example.reservation.entity.ProcessedPayment;
import com.example.reservation.entity.Reservation;
import com.example.reservation.exception.InvalidRequestException;
import com.example.reservation.exception.PaymentRejectedException;
import com.example.reservation.exception.ReservationNotFoundException;
import com.example.reservation.exception.RoomUnavailableException;
import com.example.reservation.repository.ProcessedPaymentRepository;
import com.example.reservation.repository.ReservationRepository;
import com.example.reservation.service.ReservationService;
import com.example.reservation.service.RoomPricingCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);

    private final ReservationRepository reservationRepository;
    private final ProcessedPaymentRepository processedPaymentRepository;
    private final CreditCardPaymentClient creditCardPaymentClient;
    private final RoomPricingCalculator pricingCalculator;
    private final ReservationIdGenerator idGenerator;

    public ReservationServiceImpl(ReservationRepository reservationRepository,
            ProcessedPaymentRepository processedPaymentRepository,
            CreditCardPaymentClient creditCardPaymentClient,
            RoomPricingCalculator pricingCalculator,
            ReservationIdGenerator idGenerator) {
        this.reservationRepository = reservationRepository;
        this.processedPaymentRepository = processedPaymentRepository;
        this.creditCardPaymentClient = creditCardPaymentClient;
        this.pricingCalculator = pricingCalculator;
        this.idGenerator = idGenerator;
    }

    /**
     * Validates, then checks the dates, then checks the room, then prices it - each step is cheaper
     * than the one after it, so a bad request never gets as far as a paid credit card call.
     */
    @Override
    @Transactional
    public Reservation confirmReservation(ReservationRequest request) {
        validatePaymentReference(request);
        Reservation.requireValidStay(request.reservationStartDate(), request.reservationEndDate());

        if (reservationRepository.existsOverlapping(request.roomNumber(), request.reservationStartDate(), request.reservationEndDate())) {
            throw new RoomUnavailableException("Room %s is already reserved between %s and %s."
                    .formatted(request.roomNumber(), request.reservationStartDate(), request.reservationEndDate()));
        }

        BigDecimal totalAmount = pricingCalculator.totalAmount(
                request.roomSegment(), request.reservationStartDate(), request.reservationEndDate());
        String reservationId = idGenerator.generate();

        Reservation reservation = switch (request.paymentMode()) {
            case CASH -> confirmedImmediately(request, reservationId, totalAmount);
            case CREDIT_CARD -> confirmedByCreditCard(request, reservationId, totalAmount);
            case BANK_TRANSFER -> pendingBankTransfer(request, reservationId, totalAmount);
        };

        Reservation saved = saveOrRejectOverlap(reservation);
        log.info("Reservation {} for room {} created with status {} via {}",
                saved.getId(), saved.getRoomNumber(), saved.getStatus(), saved.getPaymentMode());
        return saved;
    }

    /**
     * The existsOverlapping check above is a fast pre-check, not a guarantee - two requests for the
     * same room at the same instant could both pass it before either writes. saveAndFlush forces the
     * insert (and the database's exclusion constraint) to run here, synchronously, so that race is
     * still caught as a RoomUnavailableException instead of surfacing as a raw 500 at commit time.
     */
    private Reservation saveOrRejectOverlap(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new RoomUnavailableException("Room %s is already reserved between %s and %s."
                    .formatted(reservation.getRoomNumber(), reservation.getStartDate(), reservation.getEndDate()), e);
        }
    }

    private Reservation confirmedImmediately(ReservationRequest request, String reservationId, BigDecimal totalAmount) {
        Reservation reservation = newReservation(request, reservationId, totalAmount);
        reservation.confirm();
        return reservation;
    }

    private Reservation confirmedByCreditCard(ReservationRequest request, String reservationId, BigDecimal totalAmount) {
        PaymentStatusResponse paymentStatus = creditCardPaymentClient.retrievePaymentStatus(request.paymentReference());
        if (paymentStatus.status() != CreditCardPaymentStatus.CONFIRMED) {
            throw new PaymentRejectedException("Credit card payment for reference '%s' is %s, not CONFIRMED."
                    .formatted(request.paymentReference(), paymentStatus.status()));
        }
        Reservation reservation = newReservation(request, reservationId, totalAmount);
        reservation.confirm();
        return reservation;
    }

    private Reservation pendingBankTransfer(ReservationRequest request, String reservationId, BigDecimal totalAmount) {
        return newReservation(request, reservationId, totalAmount);
    }

    private static Reservation newReservation(ReservationRequest request, String reservationId, BigDecimal totalAmount) {
        return Reservation.reserve(reservationId, request.customerName(), request.roomNumber(),
                request.reservationStartDate(), request.reservationEndDate(), request.roomSegment(),
                request.paymentMode(), request.paymentReference(), totalAmount);
    }

    private static void validatePaymentReference(ReservationRequest request) {
        if (request.paymentMode() == PaymentMode.CREDIT_CARD
                && StringUtils.isBlank(request.paymentReference())) {
            throw new InvalidRequestException("paymentReference is required when paymentMode is CREDIT_CARD.");
        }
    }

    /** Just a lookup - JpaRepository.findById already returns an Optional, so this only adds the 404 mapping. */
    @Override
    public Reservation getReservation(String reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "No reservation found with id '%s'.".formatted(reservationId)));
    }

    /** The processedPaymentRepository check is what makes this safe against Kafka redelivering the same event. */
    @Override
    @Transactional
    public void applyBankTransferPayment(String paymentId, String reservationId, BigDecimal amountReceived) {
        if (processedPaymentRepository.existsById(paymentId)) {
            log.info("Payment {} already applied; ignoring duplicate delivery.", paymentId);
            return;
        }

        reservationRepository.findById(reservationId).ifPresentOrElse(
                reservation -> {
                    if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
                        log.warn("Payment {} references reservation {} which is already {}; ignoring.",
                                paymentId, reservationId, reservation.getStatus());
                        return;
                    }
                    boolean confirmed = reservation.applyBankTransferPayment(amountReceived);
                    reservationRepository.save(reservation);
                    processedPaymentRepository.save(new ProcessedPayment(paymentId, reservationId));
                    log.info("Applied payment {} ({}) to reservation {}; now {} of {} received{}",
                            paymentId, amountReceived, reservationId,
                            reservation.getAmountReceived(), reservation.getTotalAmount(),
                            confirmed ? " - reservation confirmed" : "");
                },
                () -> log.warn("Payment {} references unknown reservation {}; ignoring.", paymentId, reservationId));
    }

    /**
     * The repository query is a broad filter on status/mode/date; isEligibleForAutoCancellation() on
     * each candidate does the real check, since a partial payment still leaves it PENDING_PAYMENT.
     *
     * Fetches in bounded batches instead of loading every overdue reservation into memory at once.
     * Each batch always asks for page 0: cancelling a batch removes those rows from the
     * PENDING_PAYMENT filter, so "page 0" is always the next unprocessed batch - incrementing the
     * page number here would skip rows instead, since the underlying result set shrinks as we go.
     */
    @Override
    @Transactional
    public int cancelExpiredBankTransferReservations() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(ApplicationConstants.CANCELLATION_LEAD_DAYS);
        // Always page 0 - never incremented. Cancelling a batch shrinks the result set, so this same
        // request naturally serves the next unprocessed batch on every call.
        Pageable pageRequest = PageRequest.of(0, ApplicationConstants.CANCELLATION_BATCH_SIZE);

        int cancelled = 0;
        boolean hasMore = true;
        while (hasMore) {
            List<Reservation> batch = reservationRepository.findByStatusAndPaymentModeAndStartDateLessThanEqual(
                    ReservationStatus.PENDING_PAYMENT, PaymentMode.BANK_TRANSFER, cutoff, pageRequest);

            int cancelledInBatch = 0;
            for (Reservation reservation : batch) {
                if (reservation.isEligibleForAutoCancellation(today)) {
                    reservation.cancel();
                    reservationRepository.save(reservation);
                    cancelledInBatch++;
                    log.info("Auto-cancelled reservation {} for room {}: bank transfer payment incomplete "
                            + "({} of {} received) within {} day(s) of start date {}.",
                            reservation.getId(), reservation.getRoomNumber(), reservation.getAmountReceived(),
                            reservation.getTotalAmount(), ApplicationConstants.CANCELLATION_LEAD_DAYS, reservation.getStartDate());
                }
            }
            cancelled += cancelledInBatch;

            hasMore = cancelledInBatch > 0;
            if (!hasMore && !batch.isEmpty()) {
                log.warn("Auto-cancellation batch of {} candidate(s) produced no cancellations; stopping early.",
                        batch.size());
            }
        }
        if (cancelled > 0) {
            log.info("Auto-cancellation run cancelled {} reservation(s).", cancelled);
        }
        return cancelled;
    }
}
