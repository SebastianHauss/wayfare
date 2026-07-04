package com.sebastianhauss.wayfare.service;

import com.sebastianhauss.wayfare.model.ShortUrl;
import com.sebastianhauss.wayfare.model.User;
import com.sebastianhauss.wayfare.repository.RefreshTokenRepository;
import com.sebastianhauss.wayfare.repository.ShortUrlRepository;
import com.sebastianhauss.wayfare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPurgeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private AccountPurgeService purgeService;

    @BeforeEach
    void setUp() {
        purgeService = new AccountPurgeService(userRepository, refreshTokenRepository, shortUrlRepository, redisTemplate);
    }

    @Test
    void purgeExpiredAccounts_doesNothing_whenNoAccountsPastGracePeriod() {
        when(userRepository.findByDeletedAtBefore(any(Instant.class))).thenReturn(List.of());

        purgeService.purgeExpiredAccounts();

        verifyNoInteractions(shortUrlRepository, redisTemplate);
        verify(userRepository, never()).delete(any());
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
    }

    @Test
    void purgeExpiredAccounts_queriesUsersDeletedBeforeTheGracePeriodCutoff() {
        when(userRepository.findByDeletedAtBefore(any(Instant.class))).thenReturn(List.of());

        Instant before = Instant.now();
        purgeService.purgeExpiredAccounts();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).findByDeletedAtBefore(cutoffCaptor.capture());
        Instant cutoff = cutoffCaptor.getValue();

        assertThat(cutoff).isBetween(
                before.minus(AccountPurgeService.GRACE_PERIOD_DAYS, ChronoUnit.DAYS).minusSeconds(1),
                after.minus(AccountPurgeService.GRACE_PERIOD_DAYS, ChronoUnit.DAYS).plusSeconds(1));
    }

    @Test
    void purgeExpiredAccounts_deletesLinksTokensAndUser_inOrder_andEvictsCache() {
        User expiredUser = new User();
        expiredUser.setId(42L);
        expiredUser.setEmail("gone@example.com");
        when(userRepository.findByDeletedAtBefore(any(Instant.class))).thenReturn(List.of(expiredUser));

        ShortUrl link1 = new ShortUrl();
        link1.setShortCode("abc123");
        ShortUrl link2 = new ShortUrl();
        link2.setShortCode("def456");
        when(shortUrlRepository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(link1, link2));

        purgeService.purgeExpiredAccounts();

        verify(redisTemplate).delete("abc123");
        verify(redisTemplate).delete("def456");

        InOrder order = inOrder(shortUrlRepository, refreshTokenRepository, userRepository);
        order.verify(shortUrlRepository).deleteAll(List.of(link1, link2));
        order.verify(refreshTokenRepository).deleteAllByUserId(42L);
        order.verify(userRepository).delete(expiredUser);
    }

    @Test
    void purgeExpiredAccounts_purgesEachExpiredAccountIndependently() {
        User first = new User();
        first.setId(1L);
        first.setEmail("first@example.com");
        User second = new User();
        second.setId(2L);
        second.setEmail("second@example.com");
        when(userRepository.findByDeletedAtBefore(any(Instant.class))).thenReturn(List.of(first, second));
        when(shortUrlRepository.findByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        purgeService.purgeExpiredAccounts();

        verify(userRepository).delete(first);
        verify(userRepository).delete(second);
        verify(refreshTokenRepository).deleteAllByUserId(1L);
        verify(refreshTokenRepository).deleteAllByUserId(2L);
    }
}
