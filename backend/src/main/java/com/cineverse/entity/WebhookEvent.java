package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String provider;
    private String eventId;
    private String eventType;
    private Instant receivedAt = Instant.now();
    private Instant processedAt;
    private String outcome;
}
