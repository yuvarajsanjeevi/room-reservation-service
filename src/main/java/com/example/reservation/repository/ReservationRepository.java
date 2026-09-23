package com.example.reservation.repository;

import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.ReservationStatus;
import com.example.reservation.entity.Reservation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Query("""
            select case when count(r) > 0 then true else false end from Reservation r
            where r.roomNumber = :roomNumber and r.status <> com.example.reservation.domain.ReservationStatus.CANCELLED
              and r.startDate < :endDate and r.endDate > :startDate
            """)
    boolean existsOverlapping(
            @Param("roomNumber") String roomNumber,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<Reservation> findByStatusAndPaymentModeAndStartDateLessThanEqual(
            ReservationStatus status, PaymentMode paymentMode, LocalDate cutoffDate, Pageable page);
}
