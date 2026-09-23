package com.example.reservation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_payments")
public class ProcessedPayment {

    @Id
    private String paymentId;

    private String reservationId;

    private Instant processedAt;

    protected ProcessedPayment() {
        // JPA
    }

    public ProcessedPayment(String paymentId, String reservationId) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.processedAt = Instant.now();
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
