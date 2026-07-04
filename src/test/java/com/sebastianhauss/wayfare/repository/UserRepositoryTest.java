package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Test
    void findByEmail_returnsSavedUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        repository.saveAndFlush(user);

        assertThat(repository.findByEmail("user@example.com")).isPresent();
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailDoesNotExist() {
        assertThat(repository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void findByDeletedAtBefore_includesOnlyAccountsDeletedBeforeCutoff() {
        Instant cutoff = Instant.now().minusSeconds(60);

        User oldDeleted = new User();
        oldDeleted.setEmail("old-deleted-repo-test@example.com");
        oldDeleted.setPasswordHash("hashed");
        oldDeleted.setDeletedAt(cutoff.minusSeconds(10));
        repository.saveAndFlush(oldDeleted);

        User recentlyDeleted = new User();
        recentlyDeleted.setEmail("recently-deleted-repo-test@example.com");
        recentlyDeleted.setPasswordHash("hashed");
        recentlyDeleted.setDeletedAt(cutoff.plusSeconds(10));
        repository.saveAndFlush(recentlyDeleted);

        User active = new User();
        active.setEmail("active-repo-test@example.com");
        active.setPasswordHash("hashed");
        repository.saveAndFlush(active);

        List<User> result = repository.findByDeletedAtBefore(cutoff);

        assertThat(result).extracting(User::getEmail).contains("old-deleted-repo-test@example.com");
        assertThat(result).extracting(User::getEmail)
                .doesNotContain("recently-deleted-repo-test@example.com", "active-repo-test@example.com");
    }

    @Test
    void findByVerificationToken_returnsSavedUser() {
        User user = new User();
        user.setEmail("verify-repo-test@example.com");
        user.setPasswordHash("hashed");
        user.setEmailVerified(false);
        user.setVerificationToken("some-verification-token");
        repository.saveAndFlush(user);

        assertThat(repository.findByVerificationToken("some-verification-token")).isPresent();
    }

    @Test
    void findByVerificationToken_returnsEmpty_whenTokenDoesNotExist() {
        assertThat(repository.findByVerificationToken("no-such-token")).isEmpty();
    }
}
