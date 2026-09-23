package com.example.reservation.exception;

import com.example.reservation.constant.ApplicationConstants;
import java.net.URI;
import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "Not found"),

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "reservation-not-found", "Reservation not found"),

    ROOM_UNAVAILABLE(HttpStatus.CONFLICT, "room-unavailable", "Room unavailable"),

    PAYMENT_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "payment-rejected", "Payment rejected"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed"),

    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "upstream-unavailable", "Payment service unavailable"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error");

    private final HttpStatus status;
    private final String code;
    private final String title;

    ErrorCode(HttpStatus status, String code, String title) {
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(ApplicationConstants.ERROR_TYPE_BASE + code);
    }
}
