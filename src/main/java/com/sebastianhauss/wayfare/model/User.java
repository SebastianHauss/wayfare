package com.sebastianhauss.wayfare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, unique = true, nullable = false)
    private String email;

    @Column(length = 32)
    private String provider;

    @CreationTimestamp
    private Instant createdAt;

    private Instant deletedAt;
}
