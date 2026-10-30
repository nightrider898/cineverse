package com.cineverse.repository;

import com.cineverse.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
    List<City> findByActiveTrueOrderByPopularDescNameAsc();
    Optional<City> findBySlugIgnoreCase(String slug);
    Optional<City> findByNameIgnoreCase(String name);
}
