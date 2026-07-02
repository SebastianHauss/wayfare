package com.sebastianhauss.wayfare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "short_urls")
@Getter
@Setter
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 16, unique = true)
    private String shortCode;

    @Column(length = 2048, nullable = false)
    private String originalUrl;

    @CreationTimestamp
    private Instant createdAt;

    private Long clickCount = 0L;
}
