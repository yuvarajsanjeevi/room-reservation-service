package com.example.reservation.config;

import com.example.reservation.domain.RoomSegment;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "room-reservation")
public record ReservationProperties(Map<RoomSegment, BigDecimal> nightlyRates, String cancellationCheckCron) {
}
