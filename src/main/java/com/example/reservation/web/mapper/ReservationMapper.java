package com.example.reservation.web.mapper;

import com.example.reservation.entity.Reservation;
import com.example.reservation.web.dto.ReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getStatus());
    }
}
