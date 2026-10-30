package com.cineverse.repository;

import com.cineverse.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long>, JpaSpecificationExecutor<Show> {

    @Query("""
        select s from Show s join fetch s.cinema c join fetch s.screen join fetch s.movie
        where s.movie.id = :movieId and c.city.id = :cityId and s.showDate = :date
          and s.status = com.cineverse.entity.enums.ShowStatus.SCHEDULED and s.startTime > :after and c.active = true
        order by c.name, s.startTime
        """)
    List<Show> findForMovieInCity(@Param("movieId") Long movieId, @Param("cityId") Long cityId,
                                  @Param("date") LocalDate date, @Param("after") Instant after);

    @Query("""
        select s from Show s join fetch s.movie join fetch s.screen
        where s.cinema.id = :cinemaId and s.showDate = :date
          and s.status = com.cineverse.entity.enums.ShowStatus.SCHEDULED and s.startTime > :after
        order by s.movie.title, s.startTime
        """)
    List<Show> findForCinema(@Param("cinemaId") Long cinemaId, @Param("date") LocalDate date, @Param("after") Instant after);

    @Query("""
        select distinct s.showDate from Show s
        where s.movie.id = :movieId and s.cinema.city.id = :cityId and s.startTime > :after
          and s.status = com.cineverse.entity.enums.ShowStatus.SCHEDULED
        order by s.showDate
        """)
    List<LocalDate> availableDates(@Param("movieId") Long movieId, @Param("cityId") Long cityId, @Param("after") Instant after);

    @Query("""
        select distinct s.movie.id from Show s
        where s.cinema.city.id = :cityId and s.startTime > :after and s.status = com.cineverse.entity.enums.ShowStatus.SCHEDULED
        """)
    List<Long> movieIdsShowingInCity(@Param("cityId") Long cityId, @Param("after") Instant after);

    @Query("""
        select count(s) > 0 from Show s
        where s.screen.id = :screenId and s.status <> com.cineverse.entity.enums.ShowStatus.CANCELLED
          and s.startTime < :end and s.endTime > :start and (:excludeId is null or s.id <> :excludeId)
        """)
    boolean existsOverlap(@Param("screenId") Long screenId, @Param("start") Instant start, @Param("end") Instant end,
                          @Param("excludeId") Long excludeId);

    boolean existsByScreenIdAndShowDate(Long screenId, LocalDate date);

    boolean existsByScreenIdAndStartTimeAfter(Long screenId, Instant after);

    long countByStatusAndStartTimeAfter(com.cineverse.entity.enums.ShowStatus status, Instant after);

    @Query("""
        select s from Show s where s.status = com.cineverse.entity.enums.ShowStatus.SCHEDULED
          and s.startTime > :from and s.startTime <= :to
        """)
    List<Show> startingBetween(@Param("from") Instant from, @Param("to") Instant to);
}
