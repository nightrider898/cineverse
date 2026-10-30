package com.cineverse.entity;

import com.cineverse.entity.enums.PaymentMethod;
import com.cineverse.entity.enums.PaymentProviderType;
import com.cineverse.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private PaymentProviderType provider;
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency = "INR";
    private String providerOrderId;
    private String providerPaymentId;
    private String failureReason;
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant completedAt;
    @Version
    private long version;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
