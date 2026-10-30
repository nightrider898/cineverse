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
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long paymentId;
    private String type;
    private String fromStatus;
    private String toStatus;
    private String providerRef;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;
    private Instant createdAt = Instant.now();
}
