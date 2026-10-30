package com.cineverse.entity;

import com.cineverse.entity.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;
    private String ticketCode;
    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.VALID;
    private Instant issuedAt = Instant.now();
    private Instant usedAt;
    private Long validatedBy;

    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private TicketQrCode qrCode;
}
