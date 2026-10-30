package com.cineverse.repository;

import com.cineverse.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("select a from AuditLog a where (:action is null or a.action = :action) and (:resource is null or a.resource = :resource) order by a.createdAt desc")
    Page<AuditLog> search(@Param("action") String action, @Param("resource") String resource, Pageable pageable);
}
