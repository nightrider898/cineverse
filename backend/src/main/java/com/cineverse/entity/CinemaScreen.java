package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "cinema_screens")
public class CinemaScreen {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;
    private String name;
    private String screenType = "STANDARD";
    private String formats = "2D";
    private int capacity;
    private boolean active = true;

    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowIndex, colIndex")
    private List<ScreenSeat> seats = new ArrayList<>();

    public List<String> formatList() {
        return formats == null || formats.isBlank() ? List.of() : Arrays.stream(formats.split(",")).map(String::trim).toList();
    }
}
