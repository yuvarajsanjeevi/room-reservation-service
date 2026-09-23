package com.example.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reservation.config.ReservationProperties;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.exception.PricingConfigurationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomPricingCalculatorTest {

    private final RoomPricingCalculator calculator = new RoomPricingCalculator(
            new ReservationProperties(Map.of(RoomSegment.SMALL, BigDecimal.valueOf(80)), "0 0 * * * *"));

    @Test
    void multipliesTheNightlyRateByTheNumberOfNights() {
        BigDecimal total = calculator.totalAmount(RoomSegment.SMALL,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));

        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(320));
    }

    @Test
    void missingRateForASegmentFailsClearly() {
        assertThatThrownBy(() -> calculator.totalAmount(RoomSegment.EXTRA_LARGE,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
                .isInstanceOf(PricingConfigurationException.class)
                .hasMessageContaining("EXTRA_LARGE");
    }
}
