package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "food_items")
public class FoodItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cinema_id")
    private Long cinemaId;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String imageUrl;
    private boolean veg = true;
    private boolean available = true;
    private int sortOrder;
}
