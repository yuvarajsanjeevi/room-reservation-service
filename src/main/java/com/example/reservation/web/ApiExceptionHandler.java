package com.example.reservation.web;

import com.example.reservation.constant.ApplicationConstants;
import com.example.reservation.exception.ErrorCode;
import com.example.reservation.exception.InvalidRequestException;
import com.example.reservation.exception.PaymentRejectedException;
import com.example.reservation.exception.PricingConfigurationException;
import com.example.reservation.exception.ReservationNotFoundException;
import com.example.reservation.exception.RoomUnavailableException;
import com.example.reservation.exception.UpstreamRejectedException;
import com.example.reservation.exception.UpstreamUnavailableException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> onInvalidRequest(InvalidRequestException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UpstreamRejectedException.class)
    ResponseEntity<ProblemDetail> onUpstreamRejected(UpstreamRejectedException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RoomUnavailableException.class)
    ResponseEntity<ProblemDetail> onRoomUnavailable(RoomUnavailableException e) {
        return errorResponse(ErrorCode.ROOM_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(PaymentRejectedException.class)
    ResponseEntity<ProblemDetail> onPaymentRejected(PaymentRejectedException e) {
        return errorResponse(ErrorCode.PAYMENT_REJECTED, e.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ResponseEntity<ProblemDetail> onReservationNotFound(ReservationNotFoundException e) {
        return errorResponse(ErrorCode.RESERVATION_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ResponseEntity<ProblemDetail> onUpstreamUnavailable(UpstreamUnavailableException e) {
        log.warn("credit-card-payment-service unavailable", e);
        return errorResponse(ErrorCode.UPSTREAM_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(PricingConfigurationException.class)
    ResponseEntity<ProblemDetail> onPricingConfiguration(PricingConfigurationException e) {
        log.error("Pricing configuration error", e);
        return errorResponse(ErrorCode.INTERNAL_ERROR, ApplicationConstants.INTERNAL_ERROR_MESSAGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> onValidationFailure(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return errorResponse(ErrorCode.INVALID_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> onUnreadableBody(HttpMessageNotReadableException e) {
        return errorResponse(ErrorCode.INVALID_REQUEST, "The request body could not be parsed.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> onNoHandler(NoResourceFoundException e) {
        return errorResponse(ErrorCode.NOT_FOUND, "No endpoint for %s %s.".formatted(e.getHttpMethod(), e.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> onMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return errorResponse(ErrorCode.METHOD_NOT_ALLOWED, "%s is not supported here.".formatted(e.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> onUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return errorResponse(ErrorCode.INTERNAL_ERROR, ApplicationConstants.INTERNAL_ERROR_MESSAGE);
    }

    private static ResponseEntity<ProblemDetail> errorResponse(ErrorCode errorCode, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        body.setTitle(errorCode.title());
        body.setType(errorCode.type());
        return ResponseEntity.status(errorCode.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
