package com.cineverse.repository;

import com.cineverse.entity.Director;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DirectorRepository extends JpaRepository<Director, Long> {
    Optional<Director> findFirstByNameIgnoreCase(String name);
}
