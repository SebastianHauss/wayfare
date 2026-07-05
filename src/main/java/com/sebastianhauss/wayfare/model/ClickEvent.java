package com.sebastianhauss.wayfare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "click_events")
@Getter
@Setter
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shortUrlId;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant clickedAt;

    @Column(length = 255)
    private String referrerDomain;

    @Column(length = 2)
    private String country;

    @Column(length = 16)
    private String deviceType;

    @Column(length = 32)
    private String browser;
}
