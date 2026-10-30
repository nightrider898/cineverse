package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "screen_seats")
public class ScreenSeat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private CinemaScreen screen;
    private String rowLabel;
    private int seatNumber;
    private String label;
    private int rowIndex;
    private int colIndex;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "seat_type_id")
    private SeatType seatType;
    private boolean disabled;
}
