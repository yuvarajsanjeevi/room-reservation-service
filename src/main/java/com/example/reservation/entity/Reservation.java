package com.example.reservation.entity;

import com.example.reservation.constant.ApplicationConstants;
import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.exception.InvalidRequestException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private String id;

    private String customerName;

    private String roomNumber;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private RoomSegment roomSegment;

    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    private String paymentReference;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private BigDecimal totalAmount;

    private BigDecimal amountReceived;

    private Instant createdAt;

    @Version
    private Long version;

    protected Reservation() {
        // JPA
    }

    public static Reservation reserve(String id, String customerName, String roomNumber,
            LocalDate startDate, LocalDate endDate, RoomSegment roomSegment,
            PaymentMode paymentMode, String paymentReference, BigDecimal totalAmount) {

        requireValidStay(startDate, endDate);

        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setCustomerName(customerName);
        reservation.setRoomNumber(roomNumber);
        reservation.setStartDate(startDate);
        reservation.setEndDate(endDate);
        reservation.setRoomSegment(roomSegment);
        reservation.setPaymentMode(paymentMode);
        reservation.setPaymentReference(paymentReference);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
        reservation.setTotalAmount(totalAmount);
        reservation.setAmountReceived(BigDecimal.ZERO);
        reservation.setCreatedAt(Instant.now());
        return reservation;
    }

    public static void requireValidStay(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new InvalidRequestException("Both reservationStartDate and reservationEndDate are required.");
        }
        if (!endDate.isAfter(startDate)) {
            throw new InvalidRequestException(
                    "reservationEndDate (%s) must be after reservationStartDate (%s).".formatted(endDate, startDate));
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        if (nights > ApplicationConstants.MAX_STAY_DAYS) {
            throw new InvalidRequestException("A room cannot be reserved for more than %d days, but %d were requested."
                    .formatted(ApplicationConstants.MAX_STAY_DAYS, nights));
        }
    }

    public void confirm() {
        setStatus(ReservationStatus.CONFIRMED);
    }

    public void cancel() {
        setStatus(ReservationStatus.CANCELLED);
    }

    public boolean applyBankTransferPayment(BigDecimal amount) {
        setAmountReceived(amountReceived.add(amount));
        if (status == ReservationStatus.PENDING_PAYMENT && amountReceived.compareTo(totalAmount) >= 0) {
            confirm();
            return true;
        }
        return false;
    }

    public boolean isEligibleForAutoCancellation(LocalDate today) {
        return status == ReservationStatus.PENDING_PAYMENT
                && paymentMode == PaymentMode.BANK_TRANSFER
                && !today.isBefore(startDate.minusDays(ApplicationConstants.CANCELLATION_LEAD_DAYS));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public RoomSegment getRoomSegment() {
        return roomSegment;
    }

    public void setRoomSegment(RoomSegment roomSegment) {
        this.roomSegment = roomSegment;
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(BigDecimal amountReceived) {
        this.amountReceived = amountReceived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reservation other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Reservation{id=%s, roomNumber=%s, status=%s, paymentMode=%s}"
                .formatted(id, roomNumber, status, paymentMode);
    }
}
