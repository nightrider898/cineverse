package com.cineverse.repository;

import com.cineverse.entity.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSessionRepository extends JpaRepository<EventSession, Long> {
}
