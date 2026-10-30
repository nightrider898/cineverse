package com.cineverse.repository;

import com.cineverse.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByCityIdOrderByName(Long cityId);
}
