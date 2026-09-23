package com.example.reservation.domain;

import com.example.reservation.constant.ApplicationConstants;
import com.example.reservation.exception.MalformedTransactionDescriptionException;

/** Format: {@code <E2E unique id (10 chars)><reservationId (8 chars)>}, e.g. {@code "1401541457P4145478"}. */
public record TransactionDescription(String endToEndId, String reservationId) {

    public static TransactionDescription parse(String raw) {
        if (raw == null) {
            throw new MalformedTransactionDescriptionException("transactionDescription is missing.");
        }
        String trimmed = raw.strip();
        if (trimmed.length() != ApplicationConstants.TRANSACTION_DESCRIPTION_LENGTH) {
            throw new MalformedTransactionDescriptionException(
                    "transactionDescription '%s' must be exactly %d characters (%d + %d), but was %d."
                            .formatted(raw, ApplicationConstants.TRANSACTION_DESCRIPTION_LENGTH,
                                    ApplicationConstants.END_TO_END_ID_LENGTH,
                                    ApplicationConstants.RESERVATION_ID_LENGTH, trimmed.length()));
        }
        String endToEndId = trimmed.substring(0, ApplicationConstants.END_TO_END_ID_LENGTH);
        String reservationId = trimmed.substring(ApplicationConstants.END_TO_END_ID_LENGTH);
        return new TransactionDescription(endToEndId, reservationId);
    }
}
