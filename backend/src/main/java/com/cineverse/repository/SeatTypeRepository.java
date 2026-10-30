package com.cineverse.repository;

import com.cineverse.entity.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {
    Optional<SeatType> findByCode(String code);
    List<SeatType> findAllByOrderBySortOrderAsc();
}
