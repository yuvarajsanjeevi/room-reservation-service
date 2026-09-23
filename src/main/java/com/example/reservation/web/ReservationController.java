package com.example.reservation.web;

import com.example.reservation.config.OpenApiConfig;
import com.example.reservation.constant.ApplicationConstants;
import com.example.reservation.dto.ReservationRequest;
import com.example.reservation.service.ReservationService;
import com.example.reservation.web.dto.ReservationResponse;
import com.example.reservation.web.mapper.ReservationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = ApplicationConstants.API_BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reservations", description = "Confirm and look up room reservations")
public class ReservationController {

    private static final String ERROR_JSON = MediaType.APPLICATION_JSON_VALUE;

    private final ReservationService reservationService;
    private final ReservationMapper mapper;

    public ReservationController(ReservationService reservationService, ReservationMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    @ApiResponse(responseCode = "201", description = "The reservation was created, confirmed or left pending payment per its payment mode")
    @ApiResponse(responseCode = "400", description = "A missing or malformed field, or a stay longer than 30 days",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/invalid-request", "title": "Invalid request", "status": 400,
                              "detail": "A room cannot be reserved for more than 30 days, but 45 were requested.", "instance": "/api/v1/reservations"}""")))
    @ApiResponse(responseCode = "409", description = "The room is already reserved for an overlapping date range",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/room-unavailable", "title": "Room unavailable", "status": 409,
                              "detail": "Room 204 is already reserved between 2026-10-01 and 2026-10-05.", "instance": "/api/v1/reservations"}""")))
    @ApiResponse(responseCode = "422", description = "Credit card payment mode, and the referenced payment was not confirmed",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/payment-rejected", "title": "Payment rejected", "status": 422,
                              "detail": "Credit card payment for reference 'DL123456789' is REJECTED, not CONFIRMED.", "instance": "/api/v1/reservations"}""")))
    @ApiResponse(responseCode = "502", description = "credit-card-payment-service could not be reached, or kept failing after retries",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/upstream-unavailable", "title": "Payment service unavailable", "status": 502,
                              "detail": "credit-card-payment-service returned 503 SERVICE_UNAVAILABLE.", "instance": "/api/v1/reservations"}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected internal error",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/internal-error", "title": "Internal error", "status": 500,
                              "detail": "The request could not be completed.", "instance": "/api/v1/reservations"}""")))
    @Operation(summary = "Confirm a room reservation",
            description = """
                    Cash confirms immediately. Credit card is confirmed only if credit-card-payment-service \
                    reports the referenced payment as CONFIRMED, otherwise the request fails and no room is \
                    reserved. Bank transfer is booked with status PENDING_PAYMENT until a matching \
                    bank-transfer-payment-update event fully pays it, or it is auto-cancelled 2 days before \
                    the reservation start date if it never is.""")
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse confirmReservation(@Valid @RequestBody ReservationRequest request) {
        return mapper.toResponse(reservationService.confirmReservation(request));
    }

    @ApiResponse(responseCode = "200", description = "The reservation")
    @ApiResponse(responseCode = "404", description = "No reservation exists with that id",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/reservation-not-found", "title": "Reservation not found", "status": 404,
                              "detail": "No reservation found with id 'P4145478'.", "instance": "/api/v1/reservations/P4145478"}""")))
    @ApiResponse(responseCode = "500", description = "Unexpected internal error",
            content = @Content(mediaType = ERROR_JSON, schema = @Schema(ref = OpenApiConfig.ERROR_SCHEMA_REF),
                    examples = @ExampleObject(value = """
                            {"type": "https://example.com/room-reservation-service/errors/internal-error", "title": "Internal error", "status": 500,
                              "detail": "The request could not be completed.", "instance": "/api/v1/reservations/P4145478"}""")))
    @Operation(summary = "Look up a reservation by id")
    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse getReservation(@PathVariable String reservationId) {
        return mapper.toResponse(reservationService.getReservation(reservationId));
    }
}
