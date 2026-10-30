package com.cineverse.repository;

import com.cineverse.entity.ScreenSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenSeatRepository extends JpaRepository<ScreenSeat, Long> {
    List<ScreenSeat> findByScreenIdOrderByRowIndexAscColIndexAsc(Long screenId);

    @org.springframework.data.jpa.repository.Query("select count(s) > 0 from ShowSeat s where s.screenSeat.id = :seatId")
    boolean isReferenced(@org.springframework.data.repository.query.Param("seatId") Long seatId);
}
