package com.cineverse.seat;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.ShowDtos.SeatLockResponse;
import com.cineverse.entity.SeatLock;
import com.cineverse.entity.Show;
import com.cineverse.entity.ShowSeat;
import com.cineverse.entity.enums.*;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.BookingRepository;
import com.cineverse.repository.SeatLockRepository;
import com.cineverse.repository.ShowRepository;
import com.cineverse.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Authoritative seat inventory. All state changes to show_seats go through row locks
 * (SELECT ... FOR UPDATE) inside a transaction, so two customers can never hold or buy the same seat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatLockRepository seatLockRepository;
    private final BookingRepository bookingRepository;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public SeatLockResponse lockSeats(Long showId, Long userId, List<String> requestedLabels) {
        Instant now = Instant.now(clock);
        Show show = showRepository.findById(showId).orElseThrow(() -> new ApiException(ErrorCode.SHOW_NOT_FOUND));
        assertBookable(show, now);

        LinkedHashSet<String> labels = requestedLabels.stream().map(s -> s.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (labels.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Select at least one seat");
        }
        if (labels.size() > props.seatLock().maxSeats()) {
            throw new ApiException(ErrorCode.SEAT_LIMIT_EXCEEDED, "You can book up to " + props.seatLock().maxSeats() + " seats at a time");
        }

        // A customer holds at most one checkout lock per show: re-selecting replaces the previous hold.
        for (SeatLock previous : seatLockRepository.findActiveForUserAndShow(userId, showId)) {
            if (previous.getHoldType() == HoldType.CHECKOUT) {
                release(previous, SeatLockStatus.RELEASED, now, "Seats changed");
            }
        }

        List<ShowSeat> seats = showSeatRepository.lockSeats(showId, labels);
        if (seats.size() != labels.size()) {
            throw new ApiException(ErrorCode.SEAT_NOT_FOUND);
        }

        Set<UUID> heldBy = seats.stream().filter(s -> s.getStatus() == SeatStatus.LOCKED && s.getLockId() != null)
                .map(ShowSeat::getLockId).collect(Collectors.toSet());
        Map<UUID, SeatLock> liveLocks = seatLockRepository.findAllById(heldBy).stream()
                .filter(l -> l.isUsable(now)).collect(Collectors.toMap(SeatLock::getId, l -> l));

        List<String> unavailable = new ArrayList<>();
        boolean anyBooked = false;
        for (ShowSeat seat : seats) {
            switch (seat.getStatus()) {
                case BOOKED -> { anyBooked = true; unavailable.add(seat.getLabel()); }
                case BLOCKED -> unavailable.add(seat.getLabel());
                case LOCKED -> {
                    if (liveLocks.containsKey(seat.getLockId())) unavailable.add(seat.getLabel());
                    // otherwise the hold has lapsed and the cleanup job just hasn't run yet: the seat is free
                }
                case AVAILABLE -> { }
            }
        }
        if (!unavailable.isEmpty()) {
            ErrorCode code = anyBooked ? ErrorCode.SEAT_ALREADY_BOOKED : ErrorCode.SEAT_LOCKED;
            throw new ApiException(code, code.defaultMessage() + ": " + String.join(", ", unavailable),
                    Map.of("seatIds", unavailable));
        }

        SeatLock lock = new SeatLock();
        lock.setId(UUID.randomUUID());
        lock.setShowId(showId);
        lock.setUserId(userId);
        lock.setSeatLabels(String.join(",", labels));
        lock.setExpiresAt(now.plus(props.seatLock().duration()));
        seatLockRepository.save(lock);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.LOCKED);
            seat.setLockId(lock.getId());
            subtotal = subtotal.add(seat.getPrice());
        }
        log.info("seat_lock lockId={} showId={} userId={} seats={}", lock.getId(), showId, userId, labels);
        return new SeatLockResponse(lock.getId(), showId, List.copyOf(labels), lock.getExpiresAt(), now, subtotal);
    }

    /** Customer abandons their selection. */
    @Transactional
    public void releaseByUser(Long showId, UUID lockId, Long userId) {
        SeatLock lock = seatLockRepository.findForUpdate(lockId).orElseThrow(() -> ApiException.notFound("Seat lock"));
        if (!lock.getUserId().equals(userId) || !lock.getShowId().equals(showId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (lock.getStatus() == SeatLockStatus.ACTIVE) {
            release(lock, SeatLockStatus.RELEASED, Instant.now(clock), "Released by customer");
        }
    }

    /** Called by the cleanup job, one lock per transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID lockId) {
        Instant now = Instant.now(clock);
        // Same lock order as payment confirmation (booking row, then seat lock) to avoid deadlocks.
        bookingRepository.findBySeatLockId(lockId).ifPresent(b -> bookingRepository.findForUpdate(b.getId()));
        SeatLock lock = seatLockRepository.findForUpdate(lockId).orElse(null);
        if (lock == null || lock.getStatus() != SeatLockStatus.ACTIVE || lock.getExpiresAt().isAfter(now)) {
            return false;
        }
        release(lock, SeatLockStatus.EXPIRED, now, "Seat hold expired");
        return true;
    }

    /**
     * Converts a held selection into sold seats. Caller must already hold the booking row lock.
     * If the hold lapsed (e.g. a slow payment webhook) the seats are re-acquired only if nobody else took them.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumeForBooking(UUID lockId, Long userId, Collection<Long> showSeatIds) {
        Instant now = Instant.now(clock);
        SeatLock lock = seatLockRepository.findForUpdate(lockId).orElseThrow(() -> new ApiException(ErrorCode.SEAT_LOCK_EXPIRED));
        if (!lock.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Seat lock belongs to another user");
        }
        List<ShowSeat> seats = showSeatRepository.lockByIds(showSeatIds);
        boolean allHeldOrFree = seats.size() == showSeatIds.size() && seats.stream().allMatch(s ->
                (s.getStatus() == SeatStatus.LOCKED && lockId.equals(s.getLockId())) || s.getStatus() == SeatStatus.AVAILABLE
                        || (s.getStatus() == SeatStatus.LOCKED && !isLive(s.getLockId(), now)));
        if (!allHeldOrFree) {
            log.warn("seat_consume_failed lockId={} reason=seats_taken", lockId);
            return false;
        }
        if (!lock.isUsable(now)) {
            log.info("seat_consume_after_expiry lockId={} seats still free, re-acquiring", lockId);
        }
        for (ShowSeat s : seats) {
            s.setStatus(SeatStatus.BOOKED);
            s.setLockId(lockId);
        }
        lock.setStatus(SeatLockStatus.CONSUMED);
        lock.setReleasedAt(now);
        return true;
    }

    /** Extends a hold for pay-at-venue reservations. Caller holds the booking row lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant convertToVenueHold(UUID lockId, Long userId, Instant until) {
        Instant now = Instant.now(clock);
        SeatLock lock = seatLockRepository.findForUpdate(lockId).orElseThrow(() -> new ApiException(ErrorCode.SEAT_LOCK_EXPIRED));
        if (!lock.getUserId().equals(userId)) throw new ApiException(ErrorCode.FORBIDDEN);
        if (!lock.isUsable(now)) throw new ApiException(ErrorCode.SEAT_LOCK_EXPIRED);
        lock.setHoldType(HoldType.PAY_AT_VENUE);
        lock.setExpiresAt(until);
        return until;
    }

    /** Returns sold seats to inventory after a cancellation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseBookedSeats(Collection<Long> showSeatIds) {
        for (ShowSeat s : showSeatRepository.lockByIds(showSeatIds)) {
            if (s.getStatus() == SeatStatus.BOOKED) {
                s.setStatus(SeatStatus.AVAILABLE);
                s.setLockId(null);
            }
        }
        log.info("seat_release_booked seats={}", showSeatIds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseLockIfActive(UUID lockId, String reason) {
        if (lockId == null) return;
        seatLockRepository.findForUpdate(lockId).filter(l -> l.getStatus() == SeatLockStatus.ACTIVE)
                .ifPresent(l -> release(l, SeatLockStatus.RELEASED, Instant.now(clock), reason));
    }

    public boolean isUsable(UUID lockId) {
        return lockId != null && isLive(lockId, Instant.now(clock));
    }

    private boolean isLive(UUID lockId, Instant now) {
        return lockId != null && seatLockRepository.findById(lockId).map(l -> l.isUsable(now)).orElse(false);
    }

    private void release(SeatLock lock, SeatLockStatus status, Instant now, String reason) {
        lock.setStatus(status);
        lock.setReleasedAt(now);
        int released = 0;
        for (ShowSeat seat : showSeatRepository.lockByLockId(lock.getId())) {
            if (seat.getStatus() == SeatStatus.LOCKED) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setLockId(null);
                released++;
            }
        }
        bookingRepository.findBySeatLockId(lock.getId()).filter(b -> b.isOpen()).ifPresent(b -> {
            b.setStatus(status == SeatLockStatus.EXPIRED ? BookingStatus.EXPIRED : BookingStatus.CANCELLED);
            b.setCancelReason(reason);
            b.setCancelledAt(now);
        });
        log.info("seat_unlock lockId={} showId={} status={} seatsReleased={} reason={}", lock.getId(), lock.getShowId(), status, released, reason);
    }

    private void assertBookable(Show show, Instant now) {
        if (show.getStatus() != ShowStatus.SCHEDULED || !show.getStartTime().isAfter(now)) {
            throw new ApiException(ErrorCode.SHOW_NOT_BOOKABLE);
        }
    }
}
