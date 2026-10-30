package com.cineverse.mapper;

import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.dto.EventDtos;
import com.cineverse.entity.*;
import com.cineverse.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogMapper {
    private final RatingRepository ratingRepository;

    public CityResponse city(City c) {
        if (c == null) return null;
        return new CityResponse(c.getId(), c.getName(), c.getState(), c.getSlug(), c.isPopular(), c.getLatitude(), c.getLongitude());
    }

    public GenreResponse genre(Genre g) {
        return new GenreResponse(g.getId(), g.getName(), g.getSlug());
    }

    public List<MovieResponse> movies(Collection<Movie> movies) {
        if (movies.isEmpty()) return List.of();
        Map<Long, Rating> ratings = ratingRepository.findByMovieIdIn(movies.stream().map(Movie::getId).toList())
                .stream().collect(Collectors.toMap(Rating::getMovieId, Function.identity()));
        return movies.stream().map(m -> movie(m, ratings.get(m.getId()))).toList();
    }

    public MovieResponse movie(Movie m) {
        return movie(m, ratingRepository.findById(m.getId()).orElse(null));
    }

    public MovieResponse movie(Movie m, Rating rating) {
        return new MovieResponse(m.getId(), m.getTitle(), m.getSlug(), m.getTagline(), m.getPosterUrl(), m.getBackdropUrl(),
                m.getCertificate(), m.getDurationMin(), m.getReleaseDate(), m.getStatus().name(),
                List.copyOf(m.getLanguages()), m.getGenres().stream().map(Genre::getName).toList(), m.formatList(),
                rating == null ? BigDecimal.ZERO : rating.getAverage().setScale(1, RoundingMode.HALF_UP),
                rating == null ? 0 : rating.getTotalVotes(), m.isFeatured(), m.getTrailerUrl(),
                m.getImdbRating(), m.getImdbVotes(), m.getCollection(), m.isReleaseDateEstimated());
    }

    public RatingSummary ratingSummary(Rating r) {
        Map<Integer, Integer> dist = new LinkedHashMap<>();
        if (r == null) {
            for (int i = 5; i >= 1; i--) dist.put(i, 0);
            return new RatingSummary(BigDecimal.ZERO, 0, dist);
        }
        dist.put(5, r.getCount5());
        dist.put(4, r.getCount4());
        dist.put(3, r.getCount3());
        dist.put(2, r.getCount2());
        dist.put(1, r.getCount1());
        return new RatingSummary(r.getAverage().setScale(1, RoundingMode.HALF_UP), r.getTotalVotes(), dist);
    }

    public CinemaSummary cinema(Cinema c) {
        return cinema(c, null);
    }

    public CinemaSummary cinema(Cinema c, Double distanceKm) {
        return new CinemaSummary(c.getId(), c.getName(), c.getSlug(), c.getAddress(), c.getCity().getName(),
                c.getLocation() == null ? null : c.getLocation().getName(), c.getLatitude(), c.getLongitude(),
                c.facilityList(), c.isAllowPayAtVenue(), distanceKm, c.getChain());
    }

    public ScreenSummary screen(CinemaScreen s) {
        return new ScreenSummary(s.getId(), s.getName(), s.getScreenType(), s.formatList(), s.getCapacity());
    }

    public VenueSummary venue(Venue v) {
        List<String> facilities = v.getFacilities() == null || v.getFacilities().isBlank() ? List.of() : List.of(v.getFacilities().split(","));
        return new VenueSummary(v.getId(), v.getName(), v.getSlug(), v.getAddress(), v.getCity().getName(), v.getCapacity(),
                facilities, v.getLatitude(), v.getLongitude(), v.isAllowPayAtVenue());
    }

    public FoodItemResponse food(FoodItem f) {
        return new FoodItemResponse(f.getId(), f.getName(), f.getDescription(), f.getCategory(), f.getPrice(), f.getImageUrl(), f.isVeg());
    }

    public OfferResponse offer(Offer o) {
        return new OfferResponse(o.getId(), o.getTitle(), o.getDescription(), o.getOfferType(), o.getCouponCode(), o.getImageUrl(),
                o.getTerms(), o.getValidTo(), o.getMovieId(), o.getCityId());
    }

    public EventDtos.CategoryResponse category(EventCategory c) {
        return new EventDtos.CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getVertical().name(), c.getIcon());
    }

    public PersonResponse person(Actor a) {
        return new PersonResponse(a.getId(), a.getName(), a.getPhotoUrl());
    }

    /** Great-circle distance, used to sort cinemas nearest-first when the client shares its location. */
    public static Double distanceKm(Double lat, Double lng, BigDecimal lat2, BigDecimal lng2) {
        if (lat == null || lng == null || lat2 == null || lng2 == null) return null;
        double r = 6371.0;
        double dLat = Math.toRadians(lat2.doubleValue() - lat);
        double dLng = Math.toRadians(lng2.doubleValue() - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(lat2.doubleValue())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return Math.round(r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10.0) / 10.0;
    }
}
