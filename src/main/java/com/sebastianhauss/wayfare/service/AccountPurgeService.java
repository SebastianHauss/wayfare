package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.model.User;
import com.sebastianhauss.wayfare.repository.RefreshTokenRepository;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import com.sebastianhauss.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountPurgeService {

    public static final int GRACE_PERIOD_DAYS = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredAccounts() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        List<User> expired = userRepository.findByDeletedAtBefore(cutoff);
        expired.forEach(this::purge);
        if (!expired.isEmpty()) {
            log.info("Purged {} account(s) past the {}-day deletion grace period", expired.size(), GRACE_PERIOD_DAYS);
        }
    }

    private void purge(User user) {
        List<ShortUrl> links = shortUrlRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        links.forEach(link -> redisTemplate.delete(link.getShortCode()));
        shortUrlRepository.deleteAll(links);
        refreshTokenRepository.deleteAllByUserId(user.getId());
        userRepository.delete(user);
        log.info("Permanently purged account {} ({})", user.getId(), user.getEmail());
    }
}
