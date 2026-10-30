package com.cineverse.entity;

import com.cineverse.entity.enums.DiscountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "coupons")
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String description;
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount = BigDecimal.ZERO;
    private Instant validFrom;
    private Instant validTo;
    private Long movieId;
    private Long cityId;
    private Long cinemaId;
    private String paymentMethod;
    private boolean firstBookingOnly;
    private Integer usageLimit;
    private int perUserLimit = 1;
    private int timesUsed;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
