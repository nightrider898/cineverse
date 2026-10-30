package com.cineverse.entity;

import com.cineverse.entity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    private String title;
    private String body;
    private String link;
    private Instant readAt;
    private Instant createdAt = Instant.now();
}
