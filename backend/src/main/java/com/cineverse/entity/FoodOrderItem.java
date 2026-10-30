package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "food_order_items")
public class FoodOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_order_id")
    private FoodOrder foodOrder;
    @Column(name = "food_item_id")
    private Long foodItemId;
    private String itemName;
    private BigDecimal unitPrice;
    private int quantity;
}
