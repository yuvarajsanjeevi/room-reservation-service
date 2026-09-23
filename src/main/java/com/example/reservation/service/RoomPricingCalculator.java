package com.example.reservation.service;

import com.example.reservation.config.ReservationProperties;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.exception.PricingConfigurationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class RoomPricingCalculator {

    private final ReservationProperties properties;

    public RoomPricingCalculator(ReservationProperties properties) {
        this.properties = properties;
    }

    /** Prices a reservation: nightly rate for the segment times the number of nights. */
    public BigDecimal totalAmount(RoomSegment roomSegment, LocalDate startDate, LocalDate endDate) {
        BigDecimal nightlyRate = properties.nightlyRates().get(roomSegment);
        if (nightlyRate == null) {
            throw new PricingConfigurationException(
                    "No nightly rate configured for room segment %s.".formatted(roomSegment));
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        return nightlyRate.multiply(BigDecimal.valueOf(nights));
    }
}
