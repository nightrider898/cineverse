package com.cineverse.repository;

import com.cineverse.entity.Actor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActorRepository extends JpaRepository<Actor, Long> {
    @Query("select a from Actor a where lower(a.name) like lower(concat('%', :q, '%')) order by a.name")
    List<Actor> search(@Param("q") String q, Pageable pageable);

    Optional<Actor> findFirstByNameIgnoreCase(String name);
}
