package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "offers")
public class Offer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String description;
    private String offerType;
    private String couponCode;
    private Long cityId;
    private Long movieId;
    private String imageUrl;
    private String terms;
    private Instant validFrom;
    private Instant validTo;
    private boolean active = true;
    private int sortOrder;
}
