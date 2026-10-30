package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    private String fullName;
    private String phone;
    private String avatarUrl;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_city_id")
    private City preferredCity;
    private boolean enabled = true;
    private boolean emailVerified;
    private Instant lastLoginAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public boolean hasRole(String role) {
        return roles.stream().anyMatch(r -> r.getName().equals(role));
    }
}
