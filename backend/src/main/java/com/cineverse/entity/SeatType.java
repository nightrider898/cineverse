package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "seat_types")
public class SeatType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private BigDecimal priceDelta = BigDecimal.ZERO;
    private int sortOrder;
}
