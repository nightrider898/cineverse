package com.cineverse.booking;

import com.cineverse.config.AppProperties;
import com.cineverse.entity.Booking;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Configurable cancellation and pay-at-venue rules in one place. */
@Component
@RequiredArgsConstructor
public class BookingPolicy {
    private final AppProperties props;
    private final Clock clock;

    public Instant startTime(Booking b) {
        return b.getBookingType() == BookingType.MOVIE ? b.getShow().getStartTime() : b.getEventSession().getStartTime();
    }

    public Instant endTime(Booking b) {
        return b.getBookingType() == BookingType.MOVIE ? b.getShow().getEndTime() : b.getEventSession().getEndTime();
    }

    public Instant cancellationDeadline(Booking b) {
        return startTime(b).minus(Duration.ofMinutes(props.cancellation().cutoffMinutesBeforeShow()));
    }

    public boolean venueAllowsCancellation(Booking b) {
        return b.getBookingType() != BookingType.MOVIE || b.getShow().getCinema().isCancellationAllowed();
    }

    public boolean isCancellable(Booking b) {
        return b.getStatus() == BookingStatus.CONFIRMED && venueAllowsCancellation(b)
                && Instant.now(clock).isBefore(cancellationDeadline(b));
    }

    public boolean venueAllowsPayAtVenue(Booking b) {
        return b.getBookingType() == BookingType.MOVIE ? b.getShow().getCinema().isAllowPayAtVenue()
                : b.getEventSession().getEvent().getVenue().isAllowPayAtVenue();
    }

    public boolean payAtVenueAllowed(Booking b) {
        return venueAllowsPayAtVenue(b) && Instant.now(clock).isBefore(payAtVenueCutoff(b));
    }

    /** Unpaid venue reservations are released this long before the show starts. */
    public Instant payAtVenueCutoff(Booking b) {
        return startTime(b).minus(props.payAtVenue().cutoffBeforeShow());
    }

    public Instant payAtVenueHoldUntil(Booking b) {
        Instant byDuration = Instant.now(clock).plus(props.payAtVenue().holdDuration());
        Instant cutoff = payAtVenueCutoff(b);
        return byDuration.isBefore(cutoff) ? byDuration : cutoff;
    }
}
