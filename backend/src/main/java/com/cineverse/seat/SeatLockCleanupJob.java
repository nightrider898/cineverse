package com.cineverse.seat;

import com.cineverse.repository.SeatLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Marks lapsed seat holds EXPIRED and returns their seats to inventory. Lock rows are kept
 * (never deleted) for audit. Each lock is expired in its own transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatLockCleanupJob {
    private final SeatLockRepository seatLockRepository;
    private final SeatLockService seatLockService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${cineverse.seat-lock.cleanup-interval-ms:5000}", initialDelay = 5000)
    public void run() {
        List<UUID> expired = seatLockRepository.findExpiredIds(Instant.now(clock), PageRequest.of(0, 200));
        int count = 0;
        for (UUID id : expired) {
            try {
                if (seatLockService.expire(id)) count++;
            } catch (RuntimeException e) {
                log.warn("Could not expire seat lock {}: {}", id, e.getMessage());
            }
        }
        if (count > 0) {
            log.info("seat_lock_cleanup expired={}", count);
        }
    }
}
