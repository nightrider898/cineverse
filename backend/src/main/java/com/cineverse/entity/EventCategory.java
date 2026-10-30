package com.cineverse.entity;

import com.cineverse.entity.enums.EventVertical;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "event_categories")
public class EventCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    @Enumerated(EnumType.STRING)
    private EventVertical vertical;
    private String icon;
}
