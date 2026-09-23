package com.example.reservation.exception;

public class InvalidBankTransferEventException extends RuntimeException {

    public InvalidBankTransferEventException(String message) {
        super(message);
    }
}
