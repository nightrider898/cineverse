package com.cineverse.entity;

import com.cineverse.entity.enums.HoldType;
import com.cineverse.entity.enums.SeatLockStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "seat_locks")
public class SeatLock {
    @Id
    private UUID id;
    @Column(name = "show_id")
    private Long showId;
    @Column(name = "user_id")
    private Long userId;
    private String seatLabels;
    @Enumerated(EnumType.STRING)
    private SeatLockStatus status = SeatLockStatus.ACTIVE;
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    private HoldType holdType = HoldType.CHECKOUT;
    private Instant createdAt = Instant.now();
    private Instant releasedAt;

    public List<String> labels() {
        return Arrays.stream(seatLabels.split(",")).toList();
    }

    public boolean isUsable(Instant now) {
        return status == SeatLockStatus.ACTIVE && expiresAt.isAfter(now);
    }
}
