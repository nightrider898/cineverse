package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Getter @Setter
@Entity
@Table(name = "cinemas")
public class Cinema {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String slug;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String facilities = "";
    private boolean allowPayAtVenue;
    private boolean cancellationAllowed = true;
    private boolean active = true;
    private String chain;
    private String catalogSource = "ADMIN";
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "cinema")
    @OrderBy("name")
    private List<CinemaScreen> screens = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "cinema_partners", joinColumns = @JoinColumn(name = "cinema_id"), inverseJoinColumns = @JoinColumn(name = "partner_id"))
    private Set<Partner> partners = new HashSet<>();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public List<String> facilityList() {
        return facilities == null || facilities.isBlank() ? List.of() : Arrays.stream(facilities.split(",")).map(String::trim).toList();
    }
}
