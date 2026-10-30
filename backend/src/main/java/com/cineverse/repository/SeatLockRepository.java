package com.cineverse.repository;

import com.cineverse.entity.SeatLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatLockRepository extends JpaRepository<SeatLock, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SeatLock l where l.id = :id")
    Optional<SeatLock> findForUpdate(@Param("id") UUID id);

    @Query("select l.id from SeatLock l where l.status = com.cineverse.entity.enums.SeatLockStatus.ACTIVE and l.expiresAt < :now order by l.expiresAt")
    List<UUID> findExpiredIds(@Param("now") Instant now, Pageable pageable);

    @Query("select l from SeatLock l where l.userId = :userId and l.showId = :showId and l.status = com.cineverse.entity.enums.SeatLockStatus.ACTIVE")
    List<SeatLock> findActiveForUserAndShow(@Param("userId") Long userId, @Param("showId") Long showId);
}
