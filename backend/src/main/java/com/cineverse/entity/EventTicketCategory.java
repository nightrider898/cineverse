package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "event_ticket_categories")
public class EventTicketCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private EventSession session;
    private String name;
    private String description;
    private BigDecimal price;
    private int capacity;
    private int sold;
    private int held;
    private int maxPerBooking = 10;
    private int sortOrder;

    public int remaining() {
        return Math.max(0, capacity - sold - held);
    }
}
