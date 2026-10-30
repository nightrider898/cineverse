package com.cineverse.booking;

import com.cineverse.entity.Booking;
import com.cineverse.entity.BookingSeat;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.NotificationType;
import com.cineverse.notification.NotificationService;
import com.cineverse.repository.BookingRepository;
import com.cineverse.seat.SeatLockService;
import com.cineverse.service.AuditService;
import com.cineverse.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * State transitions shared by payments, venue staff, expiry jobs and cancellation.
 * Callers hold the booking row lock (findForUpdate) before calling in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingLifecycleService {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final BookingRepository bookingRepository;
    private final SeatLockService seatLockService;
    private final EventBookingService eventBookingService;
    private final TicketService ticketService;
    private final CouponService couponService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final BookingPolicy policy;
    private final Clock clock;

    /**
     * Converts a paid booking into a confirmed one: verifies the seat lock (owner + expiry), marks seats
     * BOOKED / moves event inventory to sold, issues the ticket and QR, records coupon usage.
     * Returns false if inventory could no longer be secured (caller must refund).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirm(Booking b) {
        if (b.getStatus() == BookingStatus.CONFIRMED) {
            return true;
        }
        Instant now = Instant.now(clock);
        boolean secured;
        if (b.getBookingType() == BookingType.MOVIE) {
            secured = seatLockService.consumeForBooking(b.getSeatLockId(), b.getUser().getId(),
                    b.getSeats().stream().map(BookingSeat::getShowSeatId).toList());
        } else {
            boolean holdActive = b.isOpen() && (b.getExpiresAt() == null || b.getExpiresAt().isAfter(now));
            secured = eventBookingService.commitInventory(b, holdActive);
        }
        if (!secured) {
            if (b.isOpen()) {
                b.setStatus(BookingStatus.EXPIRED);
                b.setCancelReason("Seats could not be secured after payment");
                b.setCancelledAt(now);
            }
            return false;
        }
        b.setStatus(BookingStatus.CONFIRMED);
        b.setConfirmedAt(now);
        b.setExpiresAt(null);
        b.setCancelReason(null);
        b.setCancelledAt(null);
        if (b.getFoodOrder() != null) b.getFoodOrder().setStatus("CONFIRMED");
        couponService.recordUsage(b);
        ticketService.issue(b);

        String title = TicketService.title(b);
        String seats = b.getBookingType() == BookingType.MOVIE
                ? b.getSeats().stream().map(BookingSeat::getSeatLabel).collect(Collectors.joining(", "))
                : b.getItems().stream().map(i -> i.getQuantity() + " × " + i.getCategoryName()).collect(Collectors.joining(", "));
        notificationService.notify(b.getUser().getId(), NotificationType.BOOKING_CONFIRMED, "Booking confirmed: " + title,
                b.getReference() + " · " + WHEN.format(policy.startTime(b)) + " · " + seats, "/bookings/" + b.getId());
        // The confirmation email (with payment and transaction ids) is sent by PaymentService via BookingEmails.
        auditService.record(b.getUser().getId(), "BOOKING_CONFIRMED", "BOOKING", b.getId(), Map.of("reference", b.getReference()));
        log.info("booking_confirmed bookingId={} ref={}", b.getId(), b.getReference());
        return true;
    }

    /** Releases inventory held by an unpaid booking and moves it to EXPIRED or CANCELLED. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void abandon(Booking b, BookingStatus target, String reason) {
        if (!b.isOpen()) return;
        if (b.getBookingType() == BookingType.MOVIE) {
            seatLockService.releaseLockIfActive(b.getSeatLockId(), reason);
        } else {
            eventBookingService.releaseInventory(b, false);
        }
        b.setStatus(target);
        b.setCancelReason(reason);
        b.setCancelledAt(Instant.now(clock));
        log.info("booking_abandoned bookingId={} status={} reason={}", b.getId(), target, reason);
    }

    /** Returns confirmed inventory to sale (used by cancellation). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseConfirmedInventory(Booking b) {
        if (b.getBookingType() == BookingType.MOVIE) {
            seatLockService.releaseBookedSeats(b.getSeats().stream().map(BookingSeat::getShowSeatId).toList());
        } else {
            eventBookingService.releaseInventory(b, true);
        }
        ticketService.cancel(b.getId());
        if (b.getFoodOrder() != null) b.getFoodOrder().setStatus("CANCELLED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfDue(Long bookingId) {
        Booking b = bookingRepository.findForUpdate(bookingId).orElse(null);
        Instant now = Instant.now(clock);
        if (b == null || !b.isOpen() || b.getExpiresAt() == null || b.getExpiresAt().isAfter(now)) return false;
        abandon(b, BookingStatus.EXPIRED, b.isPayAtVenue() ? "Not paid at the venue in time" : "Booking not paid in time");
        return true;
    }
}
