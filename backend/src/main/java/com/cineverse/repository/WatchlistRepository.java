package com.cineverse.repository;

import com.cineverse.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    @Query("select w from Watchlist w join fetch w.movie where w.userId = :userId order by w.movie.releaseDate")
    List<Watchlist> findForUser(@Param("userId") Long userId);

    Optional<Watchlist> findByUserIdAndMovieId(Long userId, Long movieId);

    @Query("select w from Watchlist w join fetch w.movie m where w.remindMe = true and w.remindedAt is null and m.releaseDate <= :date")
    List<Watchlist> dueReminders(@Param("date") LocalDate date);
}
