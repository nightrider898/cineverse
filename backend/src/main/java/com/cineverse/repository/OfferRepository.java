package com.cineverse.repository;

import com.cineverse.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    @Query("""
        select o from Offer o where o.active = true and o.validFrom <= :now and o.validTo > :now
          and (o.cityId is null or o.cityId = :cityId) order by o.sortOrder
        """)
    List<Offer> activeForCity(@Param("cityId") Long cityId, @Param("now") Instant now);

    List<Offer> findAllByOrderBySortOrderAsc();
}
