package com.example.reservation.constant;

public final class ApplicationConstants {

    private ApplicationConstants() {
    }

    public static final String API_BASE_PATH = "/api/v1";

    public static final String ERROR_TYPE_BASE = "https://example.com/room-reservation-service/errors/";

    public static final int MAX_STAY_DAYS = 30;

    public static final int CANCELLATION_LEAD_DAYS = 2;

    public static final int CANCELLATION_BATCH_SIZE = 200;

    public static final int RESERVATION_ID_LENGTH = 8;

    public static final int END_TO_END_ID_LENGTH = 10;

    public static final int TRANSACTION_DESCRIPTION_LENGTH = END_TO_END_ID_LENGTH + RESERVATION_ID_LENGTH;

    public static final String INTERNAL_ERROR_MESSAGE = "The request could not be completed.";
}
