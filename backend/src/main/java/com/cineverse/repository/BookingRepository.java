package com.cineverse.repository;

import com.cineverse.entity.Booking;
import com.cineverse.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);

    Optional<Booking> findByReference(String reference);

    List<Booking> findByShowIdAndStatusIn(Long showId, Collection<BookingStatus> statuses);

    List<Booking> findByEventSessionIdAndStatusIn(Long sessionId, Collection<BookingStatus> statuses);

    Optional<Booking> findBySeatLockId(UUID seatLockId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<BookingStatus> statuses);

    @Query("select b.id from Booking b where b.status in :statuses and b.expiresAt < :now order by b.expiresAt")
    List<Long> findExpiredOpenIds(@Param("statuses") Collection<BookingStatus> statuses, @Param("now") Instant now, Pageable pageable);

    /** Upcoming = confirmed (or reserved pay-at-venue) and the show/session has not started yet. */
    @Query(value = """
        select b from Booking b left join b.show s left join b.eventSession es
        where b.user.id = :userId and b.status in :statuses
          and coalesce(s.startTime, es.startTime) > :now
        order by coalesce(s.startTime, es.startTime) asc
        """, countQuery = """
        select count(b) from Booking b left join b.show s left join b.eventSession es
        where b.user.id = :userId and b.status in :statuses and coalesce(s.startTime, es.startTime) > :now
        """)
    Page<Booking> findUpcoming(@Param("userId") Long userId, @Param("statuses") Collection<BookingStatus> statuses,
                               @Param("now") Instant now, Pageable pageable);

    @Query(value = """
        select b from Booking b left join b.show s left join b.eventSession es
        where b.user.id = :userId and b.status in :statuses
          and coalesce(s.startTime, es.startTime) <= :now
        order by coalesce(s.startTime, es.startTime) desc
        """, countQuery = """
        select count(b) from Booking b left join b.show s left join b.eventSession es
        where b.user.id = :userId and b.status in :statuses and coalesce(s.startTime, es.startTime) <= :now
        """)
    Page<Booking> findPast(@Param("userId") Long userId, @Param("statuses") Collection<BookingStatus> statuses,
                           @Param("now") Instant now, Pageable pageable);

    Page<Booking> findByUserIdAndStatusInOrderByUpdatedAtDesc(Long userId, Collection<BookingStatus> statuses, Pageable pageable);

    @Query("""
        select b from Booking b join b.show s
        where b.user.id = :userId and s.movie.id = :movieId
          and b.status = com.cineverse.entity.enums.BookingStatus.CONFIRMED and s.startTime < :now
        order by s.startTime desc
        """)
    List<Booking> findWatchedBookings(@Param("userId") Long userId, @Param("movieId") Long movieId, @Param("now") Instant now);

    @Query("""
        select b from Booking b join b.show s
        where b.status = com.cineverse.entity.enums.BookingStatus.CONFIRMED and s.startTime > :from and s.startTime <= :to
        """)
    List<Booking> confirmedForShowsStartingBetween(@Param("from") Instant from, @Param("to") Instant to);
}
