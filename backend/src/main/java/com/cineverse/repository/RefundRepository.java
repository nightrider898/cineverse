package com.cineverse.repository;

import com.cineverse.entity.Refund;
import com.cineverse.entity.enums.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findFirstByBookingIdOrderByRequestedAtDesc(Long bookingId);

    Optional<Refund> findByPaymentId(Long paymentId);

    Optional<Refund> findByProviderRefundId(String providerRefundId);

    @Query("select r.id from Refund r where r.status in :statuses and r.attempts < :maxAttempts order by r.requestedAt")
    List<Long> findProcessableIds(@Param("statuses") Collection<RefundStatus> statuses, @Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findForUpdate(@Param("id") Long id);

    @Query("select r from Refund r where (:status is null or r.status = :status) order by r.requestedAt desc")
    Page<Refund> search(@Param("status") RefundStatus status, Pageable pageable);

    @Query("select r from Refund r where r.booking.user.id = :userId order by r.requestedAt desc")
    List<Refund> findForUser(@Param("userId") Long userId);
}
