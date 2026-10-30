package com.cineverse.entity;

import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String reference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING)
    private BookingType bookingType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id")
    private Show show;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_session_id")
    private EventSession eventSession;
    private UUID seatLockId;
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private BigDecimal ticketSubtotal = BigDecimal.ZERO;
    private BigDecimal foodSubtotal = BigDecimal.ZERO;
    private BigDecimal convenienceFee = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;
    private String couponCode;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    private boolean payAtVenue;
    private Instant expiresAt;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seatLabel")
    private List<BookingSeat> seats = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private FoodOrder foodOrder;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public boolean isOpen() {
        return status == BookingStatus.PENDING || status == BookingStatus.PAYMENT_PENDING;
    }

    public int ticketCount() {
        return bookingType == BookingType.MOVIE ? seats.size() : items.stream().mapToInt(BookingItem::getQuantity).sum();
    }
}
