package com.example.reservation.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationSchedulerTest {

    @Mock
    private ReservationService reservationService;

    @Test
    void delegatesToTheReservationService() {
        when(reservationService.cancelExpiredBankTransferReservations()).thenReturn(3);

        new ReservationCancellationScheduler(reservationService).cancelExpiredBankTransferReservations();

        verify(reservationService).cancelExpiredBankTransferReservations();
    }
}
