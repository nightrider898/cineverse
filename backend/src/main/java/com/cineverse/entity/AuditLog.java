package com.cineverse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter @Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    private String action;
    private String resource;
    private String resourceId;
    private String ipAddress;
    private String requestId;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    private Instant createdAt = Instant.now();
}
