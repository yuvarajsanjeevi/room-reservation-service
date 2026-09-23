package com.example.reservation.scheduler;

import com.example.reservation.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationCancellationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationCancellationScheduler.class);

    private final ReservationService reservationService;

    public ReservationCancellationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "${room-reservation.cancellation-check-cron}")
    public void cancelExpiredBankTransferReservations() {
        int cancelled = reservationService.cancelExpiredBankTransferReservations();
        log.debug("Auto-cancellation check complete: {} reservation(s) cancelled.", cancelled);
    }
}
