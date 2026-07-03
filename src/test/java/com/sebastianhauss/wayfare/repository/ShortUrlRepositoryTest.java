package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository repository;

    @Autowired
    private UserRepository userRepository;

    private Long createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        return userRepository.saveAndFlush(user).getId();
    }

    @Test
    void incrementClickCount_atomicallyBumpsCountByOne() {
        ShortUrl url = new ShortUrl();
        url.setOriginalUrl("https://example.com/some/long/path");
        url = repository.saveAndFlush(url);
        url.setShortCode("abc123");
        repository.saveAndFlush(url);

        repository.incrementClickCount("abc123");
        repository.incrementClickCount("abc123");

        ShortUrl reloaded = repository.findByShortCode("abc123").orElseThrow();
        assertThat(reloaded.getClickCount()).isEqualTo(2L);
    }

    @Test
    void findByShortCode_returnsEmpty_whenCodeDoesNotExist() {
        assertThat(repository.findByShortCode("doesNotExist")).isEmpty();
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsOnlyThatUsersLinks() {
        Long userOne = createUser("one@example.com");
        Long userTwo = createUser("two@example.com");

        ShortUrl ownedByOne = new ShortUrl();
        ownedByOne.setOriginalUrl("https://example.com/one");
        ownedByOne.setUserId(userOne);
        repository.saveAndFlush(ownedByOne);

        ShortUrl ownedByTwo = new ShortUrl();
        ownedByTwo.setOriginalUrl("https://example.com/two");
        ownedByTwo.setUserId(userTwo);
        repository.saveAndFlush(ownedByTwo);

        assertThat(repository.findByUserIdOrderByCreatedAtDesc(userOne))
                .extracting(ShortUrl::getOriginalUrl)
                .containsExactly("https://example.com/one");
    }

    @Test
    void findByShortCodeAndUserId_returnsEmpty_whenLinkBelongsToDifferentUser() {
        Long userOne = createUser("one@example.com");
        Long userTwo = createUser("two@example.com");

        ShortUrl url = new ShortUrl();
        url.setOriginalUrl("https://example.com/one");
        url.setUserId(userOne);
        url = repository.saveAndFlush(url);
        url.setShortCode("abc123");
        repository.saveAndFlush(url);

        assertThat(repository.findByShortCodeAndUserId("abc123", userTwo)).isEmpty();
        assertThat(repository.findByShortCodeAndUserId("abc123", userOne)).isPresent();
    }
}
