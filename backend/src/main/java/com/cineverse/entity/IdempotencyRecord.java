package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "idem_key")
    private String key;
    private Long userId;
    private String scope;
    private String requestHash;
    private String status;
    private Integer responseStatus;
    private String responseBody;
    private Instant createdAt = Instant.now();
    private Instant completedAt;
}
