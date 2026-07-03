package com.sebastianhauss.wayfare.security;

import com.sebastianhauss.wayfare.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-that-is-long-enough-for-hs256-signing", 900, 2592000);

    @Test
    void generateAndParseAccessToken_roundTripsUserIdAndEmail() {
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");

        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    void parseAccessToken_throws_whenTokenExpired() {
        JwtService shortLivedJwtService = new JwtService(
                "test-secret-key-that-is-long-enough-for-hs256-signing", -1, 2592000);
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

        String token = shortLivedJwtService.generateAccessToken(user);

        assertThatThrownBy(() -> shortLivedJwtService.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void hashToken_isDeterministicAndDiffersByInput() {
        String hashA = jwtService.hashToken("raw-token-a");
        String hashB = jwtService.hashToken("raw-token-a");
        String hashC = jwtService.hashToken("raw-token-b");

        assertThat(hashA).isEqualTo(hashB);
        assertThat(hashA).isNotEqualTo(hashC);
    }

    @Test
    void generateOpaqueRefreshToken_producesUniqueValues() {
        String tokenA = jwtService.generateOpaqueRefreshToken();
        String tokenB = jwtService.generateOpaqueRefreshToken();

        assertThat(tokenA).isNotEqualTo(tokenB);
    }
}
