package com.cineverse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {}

    public record FoodLine(@NotNull Long foodItemId, @Min(1) @Max(20) int quantity) {}

    public record CreateBookingRequest(@NotNull UUID lockId, @Valid @Size(max = 20) List<FoodLine> food) {}

    public record UpdateFoodRequest(@Valid @Size(max = 20) List<FoodLine> food) {}

    public record TicketLine(@NotNull Long categoryId, @Min(1) @Max(20) int quantity) {}

    public record CreateEventBookingRequest(@NotNull Long sessionId, @Valid @NotEmpty @Size(max = 5) List<TicketLine> tickets) {}

    public record CouponApplyRequest(@NotBlank @Size(max = 30) String couponCode, @NotNull Long bookingId) {}

    public record CancelRequest(@Size(max = 200) String reason) {}

    public record PriceBreakdown(BigDecimal ticketSubtotal, BigDecimal foodSubtotal, BigDecimal convenienceFee,
                                 BigDecimal tax, BigDecimal discount, BigDecimal grandTotal, String currency) {}

    public record SeatLine(String label, String seatType, BigDecimal price) {}

    public record ItemLine(Long categoryId, String name, BigDecimal unitPrice, int quantity) {}

    public record FoodOrderLine(Long foodItemId, String name, BigDecimal unitPrice, int quantity) {}

    public record TicketResponse(String ticketCode, String status, String qrToken, String qrImage, Instant issuedAt, Instant usedAt) {}

    public record RefundResponse(Long id, Long bookingId, String bookingReference, BigDecimal amount, String status,
                                 String reason, String providerRefundId, Instant requestedAt, Instant processedAt) {}

    public record BookingResponse(
            Long id, String reference, String type, String status,
            Long movieId, Long eventId, Long showId, Long sessionId, Long cinemaId,
            String title, String posterUrl, String certificate, String language, String format,
            String venueName, String venueAddress, String city, String screenName,
            Instant startTime, Instant endTime,
            List<SeatLine> seats, List<ItemLine> tickets, List<FoodOrderLine> food,
            PriceBreakdown price, String couponCode, String paymentMethod, boolean payAtVenue, boolean payAtVenueAllowed,
            Instant expiresAt, Instant confirmedAt, Instant cancelledAt,
            boolean cancellable, Instant cancellationDeadline,
            TicketResponse ticket, RefundResponse refund, Instant createdAt, Instant serverTime) {}
}
