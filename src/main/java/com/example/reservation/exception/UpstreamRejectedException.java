package com.example.reservation.exception;

public class UpstreamRejectedException extends RuntimeException {

    public UpstreamRejectedException(String message) {
        super(message);
    }
}
