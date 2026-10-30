package com.cineverse.repository;

import com.cineverse.entity.ShowSeat;
import com.cineverse.entity.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @Query("select s from ShowSeat s join fetch s.screenSeat ss where s.showId = :showId order by ss.rowIndex, ss.colIndex")
    List<ShowSeat> findSeatMap(@Param("showId") Long showId);

    /**
     * Row-level locks (SELECT ... FOR UPDATE) on the requested seats, taken in primary-key order so
     * concurrent requests for overlapping seats serialise instead of deadlocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.showId = :showId and s.label in :labels order by s.id")
    List<ShowSeat> lockSeats(@Param("showId") Long showId, @Param("labels") Collection<String> labels);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.lockId = :lockId order by s.id")
    List<ShowSeat> lockByLockId(@Param("lockId") UUID lockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.id in :ids order by s.id")
    List<ShowSeat> lockByIds(@Param("ids") Collection<Long> ids);

    /** Materialises a show's seat inventory from the screen layout in one statement. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        insert into show_seats (show_id, screen_seat_id, label, seat_type_code, price, status)
        select :showId, ss.id, ss.label, st.code, :basePrice + st.price_delta,
               case when ss.disabled then 'BLOCKED' else 'AVAILABLE' end
        from screen_seats ss join seat_types st on st.id = ss.seat_type_id
        where ss.screen_id = :screenId and ss.row_index between 1 and 999
        """, nativeQuery = true)
    int generateForShow(@Param("showId") Long showId, @Param("screenId") Long screenId, @Param("basePrice") java.math.BigDecimal basePrice);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        update show_seats s set price = :basePrice + st.price_delta
        from seat_types st where st.code = s.seat_type_code and s.show_id = :showId and s.status in ('AVAILABLE', 'LOCKED')
        """, nativeQuery = true)
    int repriceUnsold(@Param("showId") Long showId, @Param("basePrice") java.math.BigDecimal basePrice);

    long countByShowIdAndStatus(Long showId, SeatStatus status);

    long countByShowId(Long showId);

    @Query("select s.showId, s.status, count(s) from ShowSeat s where s.showId in :showIds group by s.showId, s.status")
    List<Object[]> statusCounts(@Param("showIds") Collection<Long> showIds);
}
