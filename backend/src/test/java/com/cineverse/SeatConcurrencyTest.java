package com.cineverse;

import com.cineverse.entity.Show;
import com.cineverse.entity.User;
import com.cineverse.entity.enums.SeatStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.ShowSeatRepository;
import com.cineverse.seat.SeatLockService;
import com.cineverse.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §97: two users grab the same seat at the same moment; exactly one wins. */
class SeatConcurrencyTest extends IntegrationTest {
    @Autowired SeatLockService seatLockService;
    @Autowired ShowSeatRepository showSeatRepository;

    @Test
    void twoUsersSameSeatOnlyOneSucceeds() throws Exception {
        Show show = fixtures.show(Duration.ofDays(1));
        User a = fixtures.user("USER");
        User b = fixtures.user("USER");
        List<Object> results = race(show.getId(), List.of(a.getId(), b.getId()), List.of("A1"));

        assertThat(results.stream().filter(r -> r instanceof com.cineverse.dto.ShowDtos.SeatLockResponse)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof ApiException)).hasSize(1)
                .allSatisfy(e -> assertThat(((ApiException) e).getCode()).isIn(ErrorCode.SEAT_LOCKED, ErrorCode.SEAT_ALREADY_BOOKED))
                .allSatisfy(e -> assertThat(((ApiException) e).getCode().status().value()).isEqualTo(409));
    }

    @Test
    void manyUsersOverlappingSelectionsNeverDoubleLock() throws Exception {
        Show show = fixtures.show(Duration.ofDays(1));
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < 12; i++) users.add(fixtures.user("USER").getId());

        List<Object> results = race(show.getId(), users, List.of("B3", "B4", "B5"));

        long winners = results.stream().filter(r -> !(r instanceof Throwable)).count();
        assertThat(winners).isEqualTo(1);
        var locked = showSeatRepository.findSeatMap(show.getId()).stream().filter(s -> s.getStatus() == SeatStatus.LOCKED).toList();
        assertThat(locked).hasSize(3);
        assertThat(locked.stream().map(s -> s.getLockId()).distinct()).hasSize(1);
    }

    private List<Object> race(Long showId, List<Long> userIds, List<String> seats) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(userIds.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Long userId : userIds) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return seatLockService.lockSeats(showId, userId, seats);
                } catch (ApiException e) {
                    return e;
                } catch (RuntimeException e) {
                    return e; // e.g. a detected deadlock surfaces as a locking failure (also a 409 at the API)
                }
            }));
        }
        start.countDown();
        List<Object> out = new ArrayList<>();
        for (Future<Object> f : futures) out.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        return out;
    }
}
