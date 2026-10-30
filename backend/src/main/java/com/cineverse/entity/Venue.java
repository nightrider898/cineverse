package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "venues")
public class Venue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String slug;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer capacity;
    private String facilities = "";
    private String imageUrl;
    private boolean allowPayAtVenue;
    private boolean active = true;
}
