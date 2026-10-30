package com.cineverse.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ShowDtos {
    private ShowDtos() {}

    public record SeatResponse(Long id, String label, String row, int number, int rowIndex, int colIndex,
                               String seatType, BigDecimal price, String status, boolean lockedByYou) {}

    public record SeatLayout(int rows, int columns, List<String> rowLabels, List<SeatResponse> seats) {}

    public record ActiveLock(UUID lockId, List<String> seatIds, Instant expiresAt) {}

    public record ShowResponse(Long showId, CatalogDtos.MovieResponse movie, CatalogDtos.CinemaSummary cinema,
                               CatalogDtos.ScreenSummary screen, LocalDate date, Instant startTime, Instant endTime,
                               String format, String language, List<CatalogDtos.SeatPrice> pricing,
                               SeatLayout layout, long availableSeats, long totalSeats,
                               boolean bookable, ActiveLock yourLock, Instant serverTime, int maxSeatsPerBooking) {}

    public record SeatAvailabilityResponse(Long showId, List<SeatStatusItem> seats, Instant serverTime) {}

    public record SeatStatusItem(String label, String status, boolean lockedByYou) {}

    public record SeatLockRequest(
            @NotEmpty(message = "Select at least one seat")
            @Size(max = 10, message = "You can book up to 10 seats at a time")
            List<@Pattern(regexp = "^[A-Z]{1,2}[0-9]{1,3}$", message = "Invalid seat id") String> seatIds) {}

    public record SeatLockResponse(UUID lockId, Long showId, List<String> seatIds, Instant expiresAt, Instant serverTime,
                                   BigDecimal ticketSubtotal) {}
}
