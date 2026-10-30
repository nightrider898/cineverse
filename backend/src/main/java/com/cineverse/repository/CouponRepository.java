package com.cineverse.repository;

import com.cineverse.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @Query("select c from Coupon c where upper(c.code) = upper(:code)")
    Optional<Coupon> findByCode(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findForUpdate(@Param("id") Long id);

    List<Coupon> findAllByOrderByCreatedAtDesc();
}
