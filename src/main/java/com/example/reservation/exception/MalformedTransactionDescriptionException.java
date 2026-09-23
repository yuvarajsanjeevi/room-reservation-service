package com.example.reservation.exception;

public class MalformedTransactionDescriptionException extends RuntimeException {

    public MalformedTransactionDescriptionException(String message) {
        super(message);
    }
}
