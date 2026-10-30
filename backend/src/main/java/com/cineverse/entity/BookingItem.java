package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "booking_items")
public class BookingItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;
    @Column(name = "ticket_category_id")
    private Long ticketCategoryId;
    private String categoryName;
    private BigDecimal unitPrice;
    private int quantity;
}
