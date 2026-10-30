package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "ratings")
public class Rating {
    @Id
    @Column(name = "movie_id")
    private Long movieId;
    private BigDecimal average = BigDecimal.ZERO;
    private int totalVotes;
    @Column(name = "count_1") private int count1;
    @Column(name = "count_2") private int count2;
    @Column(name = "count_3") private int count3;
    @Column(name = "count_4") private int count4;
    @Column(name = "count_5") private int count5;
    private Instant updatedAt = Instant.now();
}
