package com.example.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reservation.exception.MalformedTransactionDescriptionException;
import org.junit.jupiter.api.Test;

class TransactionDescriptionTest {

    @Test
    void splitsTheFirstTenCharactersFromTheLastEight() {
        TransactionDescription description = TransactionDescription.parse("1401541457P4145478");

        assertThat(description.endToEndId()).isEqualTo("1401541457");
        assertThat(description.reservationId()).isEqualTo("P4145478");
    }

    @Test
    void trimsSurroundingWhitespace() {
        TransactionDescription description = TransactionDescription.parse("  1401541457P4145478  ");

        assertThat(description.reservationId()).isEqualTo("P4145478");
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> TransactionDescription.parse("1401541457P414547"))
                .isInstanceOf(MalformedTransactionDescriptionException.class)
                .hasMessageContaining("18 characters");
    }

    @Test
    void rejectsTooLong() {
        assertThatThrownBy(() -> TransactionDescription.parse("1401541457P41454789"))
                .isInstanceOf(MalformedTransactionDescriptionException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> TransactionDescription.parse(null))
                .isInstanceOf(MalformedTransactionDescriptionException.class);
    }
}
