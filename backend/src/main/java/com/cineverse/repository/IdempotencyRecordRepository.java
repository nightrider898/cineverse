package com.cineverse.repository;

import com.cineverse.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    @Query("select r from IdempotencyRecord r where r.scope = :scope and coalesce(r.userId, 0) = coalesce(:userId, 0) and r.key = :key")
    Optional<IdempotencyRecord> find(@Param("scope") String scope, @Param("userId") Long userId, @Param("key") String key);
}
