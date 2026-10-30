package com.cineverse.repository;

import com.cineverse.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select e from Event e where e.status = com.cineverse.entity.enums.EventStatus.PUBLISHED and lower(e.title) like lower(concat('%', :q, '%')) and (:cityId is null or e.city.id = :cityId) order by e.title")
    List<Event> search(@Param("q") String q, @Param("cityId") Long cityId, Pageable pageable);

    @Query("select e from Event e order by e.createdAt desc")
    Page<Event> adminList(Pageable pageable);

    @Query("""
        select count(distinct e) from Event e join e.sessions s
        where e.status = com.cineverse.entity.enums.EventStatus.PUBLISHED and s.startTime > :now
        """)
    long countUpcoming(@Param("now") Instant now);

    @Query("""
        select e from Event e where e.status = com.cineverse.entity.enums.EventStatus.PUBLISHED
          and not exists (select 1 from EventSession s where s.event = e and s.startTime > :now)
        """)
    List<Event> findWithoutFutureSessions(@Param("now") Instant now);
}
