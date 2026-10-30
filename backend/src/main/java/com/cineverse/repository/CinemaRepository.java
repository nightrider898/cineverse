package com.cineverse.repository;

import com.cineverse.entity.Cinema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {
    List<Cinema> findByCityIdAndActiveTrueOrderByName(Long cityId);

    Optional<Cinema> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select c from Cinema c where c.active = true and lower(c.name) like lower(concat('%', :q, '%')) and (:cityId is null or c.city.id = :cityId) order by c.name")
    List<Cinema> search(@Param("q") String q, @Param("cityId") Long cityId, Pageable pageable);

    @Query("select c from Cinema c where (:cityId is null or c.city.id = :cityId) order by c.name")
    Page<Cinema> adminList(@Param("cityId") Long cityId, Pageable pageable);

    @Query("select c.id from Cinema c join c.partners p where p.user.id = :userId")
    List<Long> cinemaIdsForPartnerUser(@Param("userId") Long userId);

    long countByActiveTrue();
}
