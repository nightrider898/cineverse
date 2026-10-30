package com.cineverse.entity;

import com.cineverse.entity.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "show_seats")
public class ShowSeat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "show_id")
    private Long showId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_seat_id")
    private ScreenSeat screenSeat;
    private String label;
    private String seatTypeCode;
    private BigDecimal price;
    @Enumerated(EnumType.STRING)
    private SeatStatus status = SeatStatus.AVAILABLE;
    private UUID lockId;
    @Version
    private long version;
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
