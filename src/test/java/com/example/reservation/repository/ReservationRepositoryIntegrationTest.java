package com.example.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reservation.PostgresTestContainer;
import com.example.reservation.domain.PaymentMode;
import com.example.reservation.domain.RoomSegment;
import com.example.reservation.entity.Reservation;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Exercises the repository against a real Postgres, running the Flyway migration for real rather than
 * relying on Hibernate's {@code create-drop} against H2 the way {@link ReservationRepositoryTest} does.
 *
 * A full {@code @SpringBootTest} rather than {@code @DataJpaTest}: the JPA test slice deliberately
 * excludes Flyway autoconfiguration, which would leave the real Postgres schema empty and fail
 * Hibernate's {@code ddl-auto=validate} on the very first entity it checked.
 *
 * Skipped automatically when Docker is not available - see {@link PostgresTestContainer}.
 */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Import(PostgresTestContainer.class)
@EnabledIf("com.example.reservation.PostgresTestContainer#dockerAvailable")
class ReservationRepositoryIntegrationTest {

    @Autowired
    private ReservationRepository repository;

    @Test
    void theFlywayMigrationProducesAUsableSchema() {
        Reservation reservation = Reservation.reserve("R0000001", "Jane Doe", "101",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5),
                RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.valueOf(320));

        repository.save(reservation);

        assertThat(repository.findById("R0000001")).isPresent();
        assertThat(repository.existsOverlapping("101", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8))).isTrue();
    }

    /**
     * Proves the V2 exclusion constraint itself, independent of the application-level pre-check - it's
     * the backstop for two requests racing each other for the same room at the same instant.
     */
    @Test
    void theExclusionConstraintRejectsAnOverlappingRoomEvenIfTheAppCheckIsBypassed() {
        Reservation first = Reservation.reserve("R0000002", "Jane Doe", "201",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
                RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.valueOf(320));
        repository.saveAndFlush(first);

        Reservation overlapping = Reservation.reserve("R0000003", "John Roe", "201",
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 8),
                RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.valueOf(320));

        assertThatThrownBy(() -> repository.saveAndFlush(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theExclusionConstraintIgnoresCancelledReservations() {
        Reservation cancelled = Reservation.reserve("R0000004", "Jane Doe", "202",
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                RoomSegment.SMALL, PaymentMode.BANK_TRANSFER, null, BigDecimal.valueOf(320));
        cancelled.cancel();
        repository.saveAndFlush(cancelled);

        Reservation rebooked = Reservation.reserve("R0000005", "John Roe", "202",
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                RoomSegment.SMALL, PaymentMode.CASH, null, BigDecimal.valueOf(320));

        assertThat(repository.saveAndFlush(rebooked)).isNotNull();
    }
}
