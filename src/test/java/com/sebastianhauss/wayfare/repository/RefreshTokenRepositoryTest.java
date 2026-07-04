package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.RefreshToken;
import com.sebastianhauss.wayfare.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("refresh-token-repo-test@example.com");
        user.setPasswordHash("hashed");
        userId = userRepository.saveAndFlush(user).getId();
    }

    private RefreshToken newToken(Long ownerId, String tokenHash, boolean revoked) {
        RefreshToken token = new RefreshToken();
        token.setUserId(ownerId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setRevoked(revoked);
        return token;
    }

    @Test
    void revokeAllByUserId_revokesOnlyActiveTokensForThatUser() {
        User otherUser = new User();
        otherUser.setEmail("other-refresh-token-repo-test@example.com");
        otherUser.setPasswordHash("hashed");
        Long otherUserId = userRepository.saveAndFlush(otherUser).getId();

        repository.saveAndFlush(newToken(userId, "hash-active", false));
        repository.saveAndFlush(newToken(userId, "hash-already-revoked", true));
        repository.saveAndFlush(newToken(otherUserId, "hash-other-user", false));

        int updated = repository.revokeAllByUserId(userId);

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findByTokenHashAndRevokedFalse("hash-active")).isEmpty();
        assertThat(repository.findByTokenHashAndRevokedFalse("hash-other-user")).isPresent();
    }

    @Test
    void deleteAllByUserId_removesAllTokensForThatUser_andLeavesOthersIntact() {
        User otherUser = new User();
        otherUser.setEmail("other-delete-refresh-token-repo-test@example.com");
        otherUser.setPasswordHash("hashed");
        Long otherUserId = userRepository.saveAndFlush(otherUser).getId();

        repository.saveAndFlush(newToken(userId, "hash-to-delete-1", false));
        repository.saveAndFlush(newToken(userId, "hash-to-delete-2", true));
        repository.saveAndFlush(newToken(otherUserId, "hash-to-keep", false));

        repository.deleteAllByUserId(userId);

        assertThat(repository.findByTokenHashAndRevokedFalse("hash-to-delete-1")).isEmpty();
        assertThat(repository.findByTokenHashAndRevokedFalse("hash-to-keep")).isPresent();
        assertThat(countByTokenHash("hash-to-delete-2")).isZero();
        assertThat(countByTokenHash("hash-to-keep")).isEqualTo(1L);
    }

    private long countByTokenHash(String tokenHash) {
        return entityManager.createQuery("select count(t) from RefreshToken t where t.tokenHash = :hash", Long.class)
                .setParameter("hash", tokenHash)
                .getSingleResult();
    }
}
