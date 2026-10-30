package com.cineverse.booking;

import com.cineverse.dto.BookingDtos.*;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.repository.RefundRepository;
import com.cineverse.repository.TicketRepository;
import com.cineverse.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingMapper {
    private final PricingService pricingService;
    private final TicketRepository ticketRepository;
    private final RefundRepository refundRepository;
    private final TicketService ticketService;
    private final BookingPolicy policy;
    private final Clock clock;

    public BookingResponse toResponse(Booking b, boolean includeQrImage) {
        boolean movie = b.getBookingType() == BookingType.MOVIE;
        Show show = b.getShow();
        EventSession session = b.getEventSession();
        Event event = session == null ? null : session.getEvent();

        List<SeatLine> seats = b.getSeats().stream().map(s -> new SeatLine(s.getSeatLabel(), s.getSeatType(), s.getPrice())).toList();
        List<ItemLine> items = b.getItems().stream().map(i -> new ItemLine(i.getTicketCategoryId(), i.getCategoryName(), i.getUnitPrice(), i.getQuantity())).toList();
        List<FoodOrderLine> food = b.getFoodOrder() == null ? List.of() : b.getFoodOrder().getItems().stream()
                .map(f -> new FoodOrderLine(f.getFoodItemId(), f.getItemName(), f.getUnitPrice(), f.getQuantity())).toList();

        TicketResponse ticket = ticketRepository.findByBookingId(b.getId()).map(t -> ticketService.toResponse(t, includeQrImage)).orElse(null);
        RefundResponse refund = refundRepository.findFirstByBookingIdOrderByRequestedAtDesc(b.getId()).map(this::refund).orElse(null);

        return new BookingResponse(
                b.getId(), b.getReference(), b.getBookingType().name(), b.getStatus().name(),
                movie ? show.getMovie().getId() : null, movie ? null : event.getId(),
                movie ? show.getId() : null, movie ? null : session.getId(), movie ? show.getCinema().getId() : null,
                movie ? show.getMovie().getTitle() : event.getTitle(),
                movie ? show.getMovie().getPosterUrl() : event.getPosterUrl(),
                movie ? show.getMovie().getCertificate() : event.getAgeRestriction(),
                movie ? show.getLanguage() : event.getLanguages(),
                movie ? show.getFormat() : event.getCategory().getName(),
                movie ? show.getCinema().getName() : event.getVenue().getName(),
                movie ? show.getCinema().getAddress() : event.getVenue().getAddress(),
                movie ? show.getCinema().getCity().getName() : event.getCity().getName(),
                movie ? show.getScreen().getName() : null,
                policy.startTime(b), policy.endTime(b),
                seats, items, food, pricingService.breakdown(b), b.getCouponCode(),
                b.getPaymentMethod() == null ? null : b.getPaymentMethod().name(), b.isPayAtVenue(), policy.payAtVenueAllowed(b),
                b.getExpiresAt(), b.getConfirmedAt(), b.getCancelledAt(),
                policy.isCancellable(b), policy.venueAllowsCancellation(b) ? policy.cancellationDeadline(b) : null,
                ticket, refund, b.getCreatedAt(), Instant.now(clock));
    }

    public RefundResponse refund(Refund r) {
        return new RefundResponse(r.getId(), r.getBooking().getId(), r.getBooking().getReference(), r.getAmount(), r.getStatus().name(),
                r.getReason(), r.getProviderRefundId(), r.getRequestedAt(), r.getProcessedAt());
    }
}
