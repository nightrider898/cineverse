package com.cineverse.repository;

import com.cineverse.entity.EventCategory;
import com.cineverse.entity.enums.EventVertical;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {
    List<EventCategory> findByVerticalOrderByName(EventVertical vertical);
    Optional<EventCategory> findByCode(String code);
}
