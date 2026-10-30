package com.cineverse.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CityResponse(Long id, String name, String state, String slug, boolean popular, BigDecimal latitude, BigDecimal longitude) {}

    public record GenreResponse(Long id, String name, String slug) {}

    /** Card used in rails, grids and search results. */
    public record MovieResponse(Long id, String title, String slug, String tagline, String posterUrl, String backdropUrl,
                                String certificate, int durationMin, LocalDate releaseDate, String status,
                                List<String> languages, List<String> genres, List<String> formats,
                                BigDecimal rating, int votes, boolean featured, String trailerUrl,
                                BigDecimal imdbRating, Integer imdbVotes, String collection, boolean releaseDateEstimated) {}

    public record PersonResponse(Long id, String name, String photoUrl) {}

    public record CastMemberResponse(Long actorId, String name, String photoUrl, String character, String role) {}

    public record RatingSummary(BigDecimal average, int totalVotes, Map<Integer, Integer> distribution) {}

    public record MovieDetailResponse(MovieResponse movie, String synopsis, List<PersonResponse> directors,
                                      List<CastMemberResponse> cast, List<CastMemberResponse> crew,
                                      List<String> photos, RatingSummary ratingSummary) {}

    public record MovieHomeResponse(List<MovieResponse> featured, List<MovieResponse> nowShowing, List<MovieResponse> comingSoon,
                                    List<MovieResponse> trending, List<MovieResponse> topRated, List<MovieResponse> popular,
                                    List<MovieResponse> recommended, List<MovieResponse> classics) {}

    public record CinemaSummary(Long id, String name, String slug, String address, String city, String locality,
                                BigDecimal latitude, BigDecimal longitude, List<String> facilities,
                                boolean allowPayAtVenue, Double distanceKm, String chain) {}

    public record ScreenSummary(Long id, String name, String screenType, List<String> formats, int capacity) {}

    public record CinemaDetailResponse(CinemaSummary cinema, List<ScreenSummary> screens) {}

    public record SeatPrice(String seatType, String label, BigDecimal price) {}

    public record ShowtimeSlot(Long showId, Instant startTime, Instant endTime, String format, String language,
                               String screenName, BigDecimal minPrice, BigDecimal maxPrice, List<SeatPrice> prices,
                               String availability, long availableSeats, long totalSeats) {}

    public record CinemaShowtimes(CinemaSummary cinema, List<ShowtimeSlot> shows) {}

    public record MovieShowtimesResponse(MovieResponse movie, LocalDate date, List<LocalDate> availableDates,
                                         List<String> languages, List<String> formats, List<CinemaShowtimes> cinemas) {}

    public record MovieShowtimes(MovieResponse movie, List<ShowtimeSlot> shows) {}

    public record CinemaShowtimesResponse(CinemaSummary cinema, LocalDate date, List<MovieShowtimes> movies) {}

    public record FoodItemResponse(Long id, String name, String description, String category, BigDecimal price,
                                   String imageUrl, boolean veg) {}

    public record OfferResponse(Long id, String title, String description, String offerType, String couponCode,
                                String imageUrl, String terms, Instant validTo, Long movieId, Long cityId) {}

    public record SearchResponse(String query, List<MovieResponse> movies, List<EventDtos.EventResponse> events,
                                 List<CinemaSummary> cinemas, List<PersonResponse> people, List<VenueSummary> venues) {}

    public record VenueSummary(Long id, String name, String slug, String address, String city, Integer capacity,
                               List<String> facilities, BigDecimal latitude, BigDecimal longitude, boolean allowPayAtVenue) {}

    public record SearchSuggestions(List<String> recent, List<String> trending) {}
}
