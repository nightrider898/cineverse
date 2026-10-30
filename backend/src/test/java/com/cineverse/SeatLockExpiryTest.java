package com.cineverse;

import com.cineverse.entity.Show;
import com.cineverse.entity.User;
import com.cineverse.entity.enums.SeatLockStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.SeatLockRepository;
import com.cineverse.seat.SeatLockCleanupJob;
import com.cineverse.seat.SeatLockService;
import com.cineverse.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec §99: after the 5-minute hold lapses another customer can take the seat. */
class SeatLockExpiryTest extends IntegrationTest {
    @Autowired SeatLockService seatLockService;
    @Autowired SeatLockCleanupJob cleanupJob;
    @Autowired SeatLockRepository seatLockRepository;

    @Test
    void secondUserCanLockAfterExpiry() {
        Show show = fixtures.show(Duration.ofDays(1));
        User a = fixtures.user("USER");
        User b = fixtures.user("USER");

        var lockA = seatLockService.lockSeats(show.getId(), a.getId(), List.of("C7"));
        assertThatThrownBy(() -> seatLockService.lockSeats(show.getId(), b.getId(), List.of("C7")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SEAT_LOCKED);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        cleanupJob.run();

        assertThat(seatLockRepository.findById(lockA.lockId()).orElseThrow().getStatus()).isEqualTo(SeatLockStatus.EXPIRED);
        var lockB = seatLockService.lockSeats(show.getId(), b.getId(), List.of("C7"));
        assertThat(lockB.seatIds()).containsExactly("C7");
    }

    @Test
    void lapsedHoldIsFreeEvenBeforeCleanupRuns() {
        Show show = fixtures.show(Duration.ofDays(1));
        User a = fixtures.user("USER");
        User b = fixtures.user("USER");
        seatLockService.lockSeats(show.getId(), a.getId(), List.of("D1", "D2"));
        clock.advance(Duration.ofMinutes(6));
        assertThat(seatLockService.lockSeats(show.getId(), b.getId(), List.of("D2")).seatIds()).containsExactly("D2");
    }

    @Test
    void reselectingReplacesPreviousHold() {
        Show show = fixtures.show(Duration.ofDays(1));
        User a = fixtures.user("USER");
        User b = fixtures.user("USER");
        seatLockService.lockSeats(show.getId(), a.getId(), List.of("E1"));
        seatLockService.lockSeats(show.getId(), a.getId(), List.of("E2"));
        assertThat(seatLockService.lockSeats(show.getId(), b.getId(), List.of("E1")).seatIds()).containsExactly("E1");
    }
}
