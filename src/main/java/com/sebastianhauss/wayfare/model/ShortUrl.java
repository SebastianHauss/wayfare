package com.sebastianhauss.wayfare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "short_urls")
@Getter
@Setter
public class ShortUrl implements Persistable<Long> {

    @Id
    private Long id;

    @Column(length = 16, unique = true)
    private String shortCode;

    @Column(length = 2048, nullable = false)
    private String originalUrl;

    @CreationTimestamp
    private Instant createdAt;

    private Long clickCount = 0L;

    private Instant expiresAt;

    private Long maxClicks;

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
