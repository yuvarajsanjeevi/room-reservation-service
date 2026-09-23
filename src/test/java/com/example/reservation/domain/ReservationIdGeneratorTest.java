package com.example.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReservationIdGeneratorTest {

    private final ReservationIdGenerator generator = new ReservationIdGenerator();

    @Test
    void generatesAnEightCharacterIdStartingWithALetter() {
        String id = generator.generate();

        assertThat(id).hasSize(8);
        assertThat(id.charAt(0)).matches(c -> Character.isLetter(c));
        assertThat(id.substring(1)).matches("\\d{7}");
    }

    @Test
    void repeatedCallsDoNotCollide() {
        Set<String> ids = new HashSet<>();
        IntStream.range(0, 1_000).forEach(i -> ids.add(generator.generate()));

        assertThat(ids).hasSize(1_000);
    }
}
