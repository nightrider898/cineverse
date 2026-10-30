package com.cineverse.repository;

import com.cineverse.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByMovieIdIn(Collection<Long> movieIds);
}
