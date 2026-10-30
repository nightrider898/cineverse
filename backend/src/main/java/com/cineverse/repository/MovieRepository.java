package com.cineverse.repository;

import com.cineverse.entity.Movie;
import com.cineverse.entity.enums.MovieStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {
    Optional<Movie> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select m from Movie m where m.active = true and lower(m.title) like lower(concat('%', :q, '%')) order by m.releaseDate desc")
    List<Movie> search(@Param("q") String q, Pageable pageable);

    @Query("""
        select distinct m from Movie m join MovieCast mc on mc.movie = m
        where m.active = true and mc.actor.id in :actorIds
        """)
    List<Movie> findByActorIds(@Param("actorIds") List<Long> actorIds, Pageable pageable);

    List<Movie> findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus status);

    List<Movie> findByActiveTrueAndFeaturedTrueAndStatus(MovieStatus status);

    long countByActiveTrueAndStatus(MovieStatus status);

    /** Movies ordered by confirmed tickets sold in a window: the "Trending" rail. */
    @Query("""
        select b.show.movie.id from Booking b
        where b.status = com.cineverse.entity.enums.BookingStatus.CONFIRMED and b.bookingType = com.cineverse.entity.enums.BookingType.MOVIE
          and b.confirmedAt > :since
        group by b.show.movie.id order by count(b) desc
        """)
    List<Long> trendingMovieIds(@Param("since") Instant since, Pageable pageable);

    @Query("""
        select distinct m from Movie m join m.genres g
        where m.active = true and m.id <> :movieId and g.id in :genreIds
        """)
    List<Movie> similar(@Param("movieId") Long movieId, @Param("genreIds") List<Long> genreIds, Pageable pageable);

    /** Active movies whose posters/trailers were never synced, or not since {@code before}; current releases first. */
    @Query("""
        select m.id from Movie m
        where m.active = true and (m.mediaSyncedAt is null or m.mediaSyncedAt < :before)
        order by case when m.status = com.cineverse.entity.enums.MovieStatus.NOW_SHOWING then 0
                      when m.status = com.cineverse.entity.enums.MovieStatus.COMING_SOON then 1 else 2 end, m.id
        """)
    List<Long> idsNeedingMediaSync(@Param("before") Instant before);
}
