package com.cineverse.entity;

import com.cineverse.entity.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "refunds")
public class Refund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private RefundStatus status = RefundStatus.REQUESTED;
    private String reason;
    private String providerRefundId;
    private String failureReason;
    private int attempts;
    private Instant requestedAt = Instant.now();
    private Instant processedAt;
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
