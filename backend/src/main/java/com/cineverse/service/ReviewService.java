package com.cineverse.service;

import com.cineverse.config.CacheConfig;
import com.cineverse.dto.EngagementDtos.*;
import com.cineverse.entity.Booking;
import com.cineverse.entity.Movie;
import com.cineverse.entity.Rating;
import com.cineverse.entity.Review;
import com.cineverse.entity.enums.ReviewStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reviews are allowed only after the customer has watched the movie (a confirmed booking whose show has started),
 * one per user and movie (editable). Ratings are aggregated on the server from APPROVED reviews only.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ReviewResponse> forMovie(Long movieId, int page, int size) {
        return reviewRepository.findByMovieIdAndStatusOrderByCreatedAtDesc(movieId, ReviewStatus.APPROVED, PageRequest.of(page, Math.min(size, 50)))
                .map(r -> toResponse(r, null));
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> mine(Long userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> toResponse(r, movieRepository.findById(r.getMovieId()).map(Movie::getTitle).orElse(null))).toList();
    }

    @Transactional
    public ReviewResponse create(Long userId, ReviewRequest req) {
        if (reviewRepository.findByUserIdAndMovieId(userId, req.movieId()).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_REVIEW);
        }
        List<Booking> watched = bookingRepository.findWatchedBookings(userId, req.movieId(), Instant.now(clock));
        if (watched.isEmpty()) {
            throw new ApiException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        Review r = new Review();
        r.setUser(userRepository.getReferenceById(userId));
        r.setMovieId(req.movieId());
        r.setBookingId(watched.get(0).getId());
        apply(r, req);
        reviewRepository.save(r);
        return toResponse(r, null);
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewRequest req) {
        Review r = reviewRepository.findById(reviewId).filter(x -> x.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Review"));
        boolean wasApproved = r.getStatus() == ReviewStatus.APPROVED;
        apply(r, req);
        r.setStatus(ReviewStatus.PENDING); // edits go back through moderation
        if (wasApproved) recompute(r.getMovieId());
        return toResponse(r, null);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MOVIE_DETAIL, allEntries = true)
    public ReviewResponse moderate(Long reviewId, ReviewStatus status, Long adminId) {
        Review r = reviewRepository.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review"));
        r.setStatus(status);
        reviewRepository.flush();
        recompute(r.getMovieId());
        auditService.record(adminId, "REVIEW_" + status.name(), "REVIEW", reviewId, Map.of("movieId", r.getMovieId()));
        return toResponse(r, null);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> pending(int page, int size) {
        return reviewRepository.findByStatusOrderByCreatedAtAsc(ReviewStatus.PENDING, PageRequest.of(page, Math.min(size, 50)))
                .map(r -> toResponse(r, movieRepository.findById(r.getMovieId()).map(Movie::getTitle).orElse(null)));
    }

    /** Rebuilds the aggregate from approved reviews. The client never supplies averages. */
    @Transactional
    public void recompute(Long movieId) {
        Rating rating = ratingRepository.findById(movieId).orElseGet(() -> {
            Rating x = new Rating();
            x.setMovieId(movieId);
            return x;
        });
        int[] counts = new int[6];
        for (Object[] row : reviewRepository.ratingHistogram(movieId)) {
            counts[((Number) row[0]).intValue()] = ((Number) row[1]).intValue();
        }
        int total = 0;
        long sum = 0;
        for (int i = 1; i <= 5; i++) { total += counts[i]; sum += (long) i * counts[i]; }
        rating.setCount1(counts[1]);
        rating.setCount2(counts[2]);
        rating.setCount3(counts[3]);
        rating.setCount4(counts[4]);
        rating.setCount5(counts[5]);
        rating.setTotalVotes(total);
        rating.setAverage(total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
        rating.setUpdatedAt(Instant.now(clock));
        ratingRepository.save(rating);
    }

    private void apply(Review r, ReviewRequest req) {
        r.setRating((short) req.rating());
        r.setTitle(clean(req.title()));
        r.setBody(clean(req.body()));
    }

    /** Stored as plain text; the frontend renders it as text (never as HTML), which prevents stored XSS. */
    private static String clean(String s) {
        if (s == null) return null;
        String t = s.strip().replaceAll("[\\p{Cntrl}&&[^\n]]", "");
        return t.isEmpty() ? null : t;
    }

    private ReviewResponse toResponse(Review r, String movieTitle) {
        String name = r.getUser().getFullName();
        String display = name.contains(" ") ? name.substring(0, name.indexOf(' ')) + " " + name.charAt(name.lastIndexOf(' ') + 1) + "." : name;
        return new ReviewResponse(r.getId(), r.getMovieId(), movieTitle, display, r.getRating(), r.getTitle(), r.getBody(),
                r.getStatus().name(), r.getCreatedAt(), r.getBookingId() != null);
    }
}
