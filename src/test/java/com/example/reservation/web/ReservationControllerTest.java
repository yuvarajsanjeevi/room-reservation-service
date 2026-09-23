package com.example.reservation.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.entity.Reservation;
import com.example.reservation.exception.PaymentRejectedException;
import com.example.reservation.exception.ReservationNotFoundException;
import com.example.reservation.exception.RoomUnavailableException;
import com.example.reservation.exception.UpstreamUnavailableException;
import com.example.reservation.service.ReservationService;
import com.example.reservation.web.mapper.ReservationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Routing, JSON shape and the exception-to-status mapping. The service is mocked. */
@WebMvcTest(ReservationController.class)
@Import(ReservationMapper.class)
class ReservationControllerTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 5);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void confirmingACashReservationReturns201() {
        when(reservationService.confirmReservation(any())).thenReturn(confirmedReservation());

        mvc.post().uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("CASH", null))
                .assertThat()
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.reservationId").isEqualTo("R0000001");
    }

    @Test
    void blankCustomerNameReturns400() {
        mvc.post().uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerName":"","roomNumber":"101","reservationStartDate":"2026-10-01",
                         "reservationEndDate":"2026-10-05","roomSegment":"SMALL","paymentMode":"CASH"}
                        """)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void roomUnavailableReturns409() {
        when(reservationService.confirmReservation(any())).thenThrow(new RoomUnavailableException("already booked"));

        mvc.post().uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("CASH", null))
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void rejectedCreditCardPaymentReturns422() {
        when(reservationService.confirmReservation(any())).thenThrow(new PaymentRejectedException("not confirmed"));

        mvc.post().uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("CREDIT_CARD", "DL123456789"))
                .assertThat()
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unavailablePaymentServiceReturns502() {
        when(reservationService.confirmReservation(any())).thenThrow(new UpstreamUnavailableException("down"));

        mvc.post().uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("CREDIT_CARD", "DL123456789"))
                .assertThat()
                .hasStatus(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void gettingAKnownReservationReturns200() {
        when(reservationService.getReservation("R0000001")).thenReturn(confirmedReservation());

        mvc.get().uri("/api/v1/reservations/{id}", "R0000001")
                .assertThat()
                .hasStatusOk()
                .bodyJson().extractingPath("$.reservationStatus").isEqualTo("CONFIRMED");
    }

    @Test
    void gettingAnUnknownReservationReturns404() {
        when(reservationService.getReservation(eq("UNKNOWN1")))
                .thenThrow(new ReservationNotFoundException("no such reservation"));

        mvc.get().uri("/api/v1/reservations/{id}", "UNKNOWN1")
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static Reservation confirmedReservation() {
        Reservation reservation = Reservation.reserve("R0000001", "Jane Doe", "101", START, END,
                RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.valueOf(320));
        reservation.confirm();
        return reservation;
    }

    private static String requestJson(String paymentMode, String paymentReference) {
        return """
                {"customerName":"Jane Doe","roomNumber":"101","reservationStartDate":"2026-10-01",
                 "reservationEndDate":"2026-10-05","roomSegment":"SMALL","paymentMode":"%s","paymentReference":%s}
                """.formatted(paymentMode, paymentReference == null ? "null" : "\"" + paymentReference + "\"");
    }
}
