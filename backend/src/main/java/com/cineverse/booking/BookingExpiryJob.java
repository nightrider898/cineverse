package com.cineverse.booking;

import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;

/** Expires unpaid bookings (event holds, abandoned checkouts, unpaid pay-at-venue reservations). */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {
    private final BookingRepository bookingRepository;
    private final BookingLifecycleService lifecycle;
    private final Clock clock;

    @Scheduled(fixedDelay = 10_000, initialDelay = 7_000)
    public void run() {
        var ids = bookingRepository.findExpiredOpenIds(EnumSet.of(BookingStatus.PENDING, BookingStatus.PAYMENT_PENDING),
                Instant.now(clock), PageRequest.of(0, 200));
        int n = 0;
        for (Long id : ids) {
            try {
                if (lifecycle.expireIfDue(id)) n++;
            } catch (RuntimeException e) {
                log.warn("Could not expire booking {}: {}", id, e.getMessage());
            }
        }
        if (n > 0) log.info("booking_expiry expired={}", n);
    }
}
