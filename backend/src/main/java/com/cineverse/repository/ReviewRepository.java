package com.cineverse.repository;

import com.cineverse.entity.Review;
import com.cineverse.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByMovieIdAndStatusOrderByCreatedAtDesc(Long movieId, ReviewStatus status, Pageable pageable);

    Optional<Review> findByUserIdAndMovieId(Long userId, Long movieId);

    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);

    @Query("select r.rating, count(r) from Review r where r.movieId = :movieId and r.status = com.cineverse.entity.enums.ReviewStatus.APPROVED group by r.rating")
    List<Object[]> ratingHistogram(@Param("movieId") Long movieId);
}
