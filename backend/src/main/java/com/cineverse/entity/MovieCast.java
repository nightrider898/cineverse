package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "movie_cast")
public class MovieCast {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id")
    private Movie movie;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "actor_id")
    private Actor actor;
    private String characterName;
    private String crewRole;
    private int billingOrder;
}
