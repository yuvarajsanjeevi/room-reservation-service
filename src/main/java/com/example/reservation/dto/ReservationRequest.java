package com.example.reservation.dto;

import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.RoomSegment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReservationRequest(

        @Schema(example = "Jane Doe")
        @NotBlank String customerName,

        @Schema(example = "204")
        @NotBlank String roomNumber,

        @Schema(example = "2026-10-01")
        @NotNull LocalDate reservationStartDate,

        @Schema(example = "2026-10-05")
        @NotNull LocalDate reservationEndDate,

        @NotNull RoomSegment roomSegment,

        @NotNull PaymentMode paymentMode,

        @Schema(example = "DL123456789")
        String paymentReference) {
}
