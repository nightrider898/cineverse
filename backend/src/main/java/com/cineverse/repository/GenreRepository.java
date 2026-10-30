package com.cineverse.repository;

import com.cineverse.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    List<Genre> findAllByOrderByNameAsc();
    List<Genre> findByNameIn(Collection<String> names);
}
