package com.example.reservation.repository;

import com.example.reservation.entity.ProcessedPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPaymentRepository extends JpaRepository<ProcessedPayment, String> {
}
