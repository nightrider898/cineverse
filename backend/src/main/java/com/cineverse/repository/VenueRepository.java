package com.cineverse.repository;

import com.cineverse.entity.Venue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    Optional<Venue> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Venue> findByCityIdAndActiveTrueOrderByName(Long cityId);

    @Query("select v from Venue v where v.active = true and lower(v.name) like lower(concat('%', :q, '%')) and (:cityId is null or v.city.id = :cityId) order by v.name")
    List<Venue> search(@Param("q") String q, @Param("cityId") Long cityId, Pageable pageable);
}
