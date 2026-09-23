package com.example.reservation.domain;

import com.example.reservation.constant.ApplicationConstants;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ReservationIdGenerator {

    // no I, O, 0 - easy to mix up
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder id = new StringBuilder(ApplicationConstants.RESERVATION_ID_LENGTH);
        id.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        while (id.length() < ApplicationConstants.RESERVATION_ID_LENGTH) {
            id.append(random.nextInt(10));
        }
        return id.toString();
    }
}
