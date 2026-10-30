package com.cineverse.admin;

import com.cineverse.dto.BookingDtos.BookingResponse;
import com.cineverse.entity.enums.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {}

    // ---------------------------------------------------------------- movies
    public record CastInput(@NotBlank String name, String character, String crewRole) {}

    public record MovieUpsertRequest(
            @NotBlank @Size(max = 160) String title, @Size(max = 240) String tagline, @NotBlank String synopsis,
            @Min(30) @Max(400) int durationMin, @NotBlank @Pattern(regexp = "U|UA|UA7\\+|UA13\\+|UA16\\+|A|S") String certificate,
            @NotNull LocalDate releaseDate, @Size(max = 500) String posterUrl, @Size(max = 500) String backdropUrl,
            @Size(max = 500) String trailerUrl, @NotEmpty List<@Pattern(regexp = "2D|3D|IMAX|4DX|DOLBY_ATMOS") String> formats,
            @NotBlank @Pattern(regexp = "NOW_SHOWING|COMING_SOON|CLASSIC|ARCHIVED") String status, boolean featured, boolean active,
            @NotEmpty List<@NotBlank String> languages, @NotEmpty List<@NotBlank String> genres,
            List<@NotBlank String> directors, @Valid List<CastInput> cast,
            /* true: poster/trailer are managed by hand; false: let the TMDB sync manage them; null: infer from edits */
            Boolean mediaLocked) {}

    public record AdminMovieResponse(Long id, String title, String slug, String tagline, String synopsis, int durationMin,
                                     String certificate, LocalDate releaseDate, String posterUrl, String backdropUrl, String trailerUrl,
                                     List<String> formats, String status, boolean featured, boolean active, List<String> languages,
                                     List<String> genres, List<String> directors, List<CastInput> cast, BigDecimal rating, int votes,
                                     Integer tmdbId, boolean mediaLocked, java.time.Instant mediaSyncedAt, String mediaSyncNote,
                                     String collection, BigDecimal imdbRating, String catalogSource) {}

    // ---------------------------------------------------------------- cinemas, venues, screens
    public record CinemaUpsertRequest(@NotBlank @Size(max = 160) String name, @NotNull Long cityId, @Size(max = 120) String locality,
                                      @NotBlank @Size(max = 300) String address, BigDecimal latitude, BigDecimal longitude,
                                      List<String> facilities, boolean allowPayAtVenue, boolean cancellationAllowed, boolean active) {}

    public record AdminCinemaResponse(Long id, String name, String slug, Long cityId, String city, String locality, String address,
                                      BigDecimal latitude, BigDecimal longitude, List<String> facilities, boolean allowPayAtVenue,
                                      boolean cancellationAllowed, boolean active, int screens) {}

    public record VenueUpsertRequest(@NotBlank String name, @NotNull Long cityId, @NotBlank String address, BigDecimal latitude,
                                     BigDecimal longitude, Integer capacity, List<String> facilities, String imageUrl,
                                     boolean allowPayAtVenue, boolean active) {}

    public record ScreenUpsertRequest(@NotNull Long cinemaId, @NotBlank @Size(max = 60) String name,
                                      @NotBlank @Pattern(regexp = "STANDARD|IMAX|4DX|DOLBY") String screenType,
                                      @NotEmpty List<String> formats, boolean active) {}

    /**
     * One row of the visual seat configurator. Seats are numbered 1..seatCount; {@code aisleAfter} inserts a
     * gap after the given seat numbers; {@code seatTypeOverrides} sets individual seats to another type.
     */
    public record RowInput(@NotBlank @Pattern(regexp = "[A-Z]{1,2}") String label, @NotBlank String seatType,
                           @Min(1) @Max(60) int seatCount, int offset, List<Integer> aisleAfter, List<Integer> disabledSeats,
                           Map<Integer, String> seatTypeOverrides) {}

    public record SeatLayoutRequest(@NotEmpty @Size(max = 40) List<@Valid RowInput> rows) {}

    public record AdminScreenResponse(Long id, Long cinemaId, String cinemaName, String name, String screenType, List<String> formats,
                                      int capacity, boolean active, List<RowInput> layout, boolean hasUpcomingShows) {}

    public record SeatTypeResponse(Long id, String code, String name, BigDecimal priceDelta) {}

    // ---------------------------------------------------------------- shows
    public record ShowCreateRequest(@NotNull Long movieId, @NotNull Long screenId, @NotNull LocalDate date, @NotNull LocalTime startTime,
                                    LocalTime endTime, @NotBlank String format, @NotBlank String language,
                                    @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal basePrice) {}

    public record ShowUpdateRequest(@DecimalMin("0") BigDecimal basePrice, @Pattern(regexp = "SCHEDULED|CANCELLED") String status) {}

    public record AdminShowResponse(Long id, Long movieId, String movieTitle, Long cinemaId, String cinemaName, Long screenId, String screenName,
                                    LocalDate date, Instant startTime, Instant endTime, String format, String language, BigDecimal basePrice,
                                    String status, long booked, long total) {}

    // ---------------------------------------------------------------- events
    public record CategoryInput(Long id, @NotBlank String name, String description, @NotNull @DecimalMin("0") BigDecimal price,
                                @Min(0) int capacity, @Min(1) @Max(20) int maxPerBooking) {}

    public record SessionInput(Long id, @NotNull Instant startTime, @NotNull Instant endTime, @Valid @NotEmpty List<CategoryInput> categories) {}

    public record EventUpsertRequest(@NotBlank @Size(max = 160) String title, @NotBlank String description, @NotBlank String categoryCode,
                                     @NotNull Long venueId, String artist, String teamHome, String teamAway, String languages,
                                     Integer durationMin, String ageRestriction, String posterUrl, String bannerUrl, boolean featured,
                                     @Pattern(regexp = "DRAFT|PUBLISHED|CANCELLED") String status, @Valid @NotEmpty List<SessionInput> sessions) {}

    public record AdminEventResponse(Long id, String title, String slug, String description, String categoryCode, String vertical,
                                     Long venueId, String venueName, String city, String artist, String teamHome, String teamAway,
                                     String languages, Integer durationMin, String ageRestriction, String posterUrl, String bannerUrl,
                                     boolean featured, String status, List<AdminSession> sessions) {}

    public record AdminSession(Long id, Instant startTime, Instant endTime, List<AdminCategory> categories) {}

    public record AdminCategory(Long id, String name, String description, BigDecimal price, int capacity, int sold, int held, int maxPerBooking) {}

    // ---------------------------------------------------------------- food, coupons, offers
    public record FoodUpsertRequest(Long cinemaId, @NotBlank String name, String description,
                                    @NotBlank @Pattern(regexp = "POPCORN|BEVERAGE|SNACK|COMBO") String category,
                                    @NotNull @DecimalMin("0") BigDecimal price, String imageUrl, boolean veg, boolean available, int sortOrder) {}

    public record AdminFoodResponse(Long id, Long cinemaId, String name, String description, String category, BigDecimal price,
                                    String imageUrl, boolean veg, boolean available, int sortOrder) {}

    public record CouponUpsertRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{3,30}") String code, @NotBlank String description,
                                      @NotNull DiscountType discountType, @NotNull @DecimalMin("0.01") BigDecimal discountValue,
                                      BigDecimal maxDiscount, @NotNull @DecimalMin("0") BigDecimal minOrderAmount,
                                      @NotNull Instant validFrom, @NotNull Instant validTo, Long movieId, Long cityId, Long cinemaId,
                                      @Pattern(regexp = "^$|CARD|UPI|NETBANKING|WALLET|PHONEPE|GPAY") String paymentMethod,
                                      boolean firstBookingOnly, Integer usageLimit, @Min(1) int perUserLimit, boolean active) {}

    public record AdminCouponResponse(Long id, String code, String description, String discountType, BigDecimal discountValue,
                                      BigDecimal maxDiscount, BigDecimal minOrderAmount, Instant validFrom, Instant validTo,
                                      Long movieId, Long cityId, Long cinemaId, String paymentMethod, boolean firstBookingOnly,
                                      Integer usageLimit, int perUserLimit, int timesUsed, boolean active) {}

    public record OfferUpsertRequest(@NotBlank String title, @NotBlank String description,
                                     @NotBlank @Pattern(regexp = "WEEKEND|STUDENT|BANK|UPI|FIRST_BOOKING|MOVIE") String offerType,
                                     String couponCode, Long cityId, Long movieId, String imageUrl, String terms,
                                     @NotNull Instant validFrom, @NotNull Instant validTo, boolean active, int sortOrder) {}

    public record AdminOfferResponse(Long id, String title, String description, String offerType, String couponCode, Long cityId,
                                     Long movieId, String imageUrl, String terms, Instant validFrom, Instant validTo, boolean active, int sortOrder) {}

    // ---------------------------------------------------------------- users, bookings, audit
    public record AdminUserResponse(Long id, String fullName, String email, String phone, List<String> roles, boolean enabled,
                                    Instant createdAt, Instant lastLoginAt) {}

    public record UserUpdateRequest(@NotEmpty List<@Pattern(regexp = "USER|ADMIN|PARTNER|VENUE_STAFF") String> roles, boolean enabled) {}

    public record AdminBookingRow(BookingResponse booking, Long userId, String userName, String userEmail) {}

    public record AuditLogResponse(Long id, Long userId, String action, String resource, String resourceId, String ipAddress,
                                   String requestId, Map<String, Object> metadata, Instant createdAt) {}

    // ---------------------------------------------------------------- analytics
    public record DashboardResponse(long totalUsers, long todaysBookings, BigDecimal todaysRevenue, long activeMovies,
                                    long activeShows, long upcomingEvents, long pendingRefunds, long pendingReviews,
                                    List<Point> revenueLast14Days, List<Point> bookingsLast14Days, List<Point> signupsLast14Days,
                                    List<Point> occupancyLast14Days) {}

    public record Point(String label, BigDecimal value) {}

    public record Ranked(String name, long count, BigDecimal amount) {}

    public record AnalyticsResponse(LocalDate from, LocalDate to, BigDecimal grossSales, BigDecimal netSales, BigDecimal refunds,
                                    BigDecimal discounts, long bookings, long ticketsSold, BigDecimal averageTicketValue,
                                    BigDecimal occupancyPercent, List<Point> revenueByDay, List<Point> bookingsByDay,
                                    List<Ranked> popularMovies, List<Ranked> popularCinemas, List<Ranked> popularShowtimes,
                                    List<Ranked> couponUsage, List<Ranked> paymentMethods, List<Ranked> bookingStatus) {}
}
