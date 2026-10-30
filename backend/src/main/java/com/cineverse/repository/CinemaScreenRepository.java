package com.cineverse.repository;

import com.cineverse.entity.CinemaScreen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CinemaScreenRepository extends JpaRepository<CinemaScreen, Long> {
    List<CinemaScreen> findByCinemaIdOrderByName(Long cinemaId);

    List<CinemaScreen> findByActiveTrue();
}
