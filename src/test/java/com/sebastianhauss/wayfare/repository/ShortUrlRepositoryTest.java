package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.ShortUrl;
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
}
