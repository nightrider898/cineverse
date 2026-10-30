package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "coupon_usage")
public class CouponUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long couponId;
    private Long userId;
    private Long bookingId;
    private BigDecimal discount;
    private Instant usedAt = Instant.now();
}
