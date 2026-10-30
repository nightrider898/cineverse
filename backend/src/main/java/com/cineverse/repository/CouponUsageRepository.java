package com.cineverse.repository;

import com.cineverse.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    long countByCouponIdAndUserId(Long couponId, Long userId);

    boolean existsByBookingId(Long bookingId);

    @Query("select c.code, count(u), coalesce(sum(u.discount), 0) from CouponUsage u, Coupon c where c.id = u.couponId group by c.code order by count(u) desc")
    List<Object[]> usageSummary();
}
