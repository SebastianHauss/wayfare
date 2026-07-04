package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true where r.userId = :userId and r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken r where r.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
