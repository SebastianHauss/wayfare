package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByDeletedAtBefore(Instant cutoff);

    Optional<User> findByVerificationToken(String verificationToken);

    Optional<User> findByPasswordResetToken(String passwordResetToken);
}
