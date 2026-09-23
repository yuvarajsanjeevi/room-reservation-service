package com.example.reservation.web.dto;

import com.example.reservation.domain.ReservationStatus;

public record ReservationResponse(String reservationId, ReservationStatus reservationStatus) {
}
