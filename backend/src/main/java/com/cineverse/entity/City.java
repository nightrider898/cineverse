package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "cities")
public class City {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String state;
    private String slug;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @Column(name = "is_popular")
    private boolean popular;
    private boolean active = true;
}
