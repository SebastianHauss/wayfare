package com.sebastianhauss.wayfare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebastianhauss.wayfare.dto.ErrorResponse;
import com.sebastianhauss.wayfare.security.CsrfHeaderFilter;
import com.sebastianhauss.wayfare.security.JwtAuthenticationFilter;
import com.sebastianhauss.wayfare.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CsrfHeaderFilter csrfHeaderFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Value("${app.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public JwtDecoderFactory<ClientRegistration> oidcJwtDecoderFactory() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
            return execution.execute(request, body);
        });

        return clientRegistration -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withJwkSetUri(clientRegistration.getProviderDetails().getJwkSetUri())
                    .restOperations(restTemplate)
                    .build();
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators
                    .createDefaultWithIssuer(clientRegistration.getProviderDetails().getIssuerUri());
            OAuth2TokenValidator<Jwt> idTokenValidator = new OidcIdTokenValidator(clientRegistration);
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, idTokenValidator));
            return decoder;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectMapper objectMapper, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/shorten").permitAll()
                        .requestMatchers("/{code}", "/{code}/qr").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            log.warn("OAuth2 sign-in failed: {}", exception.getMessage(), exception);
                            response.sendRedirect(frontendUrl + "/auth/callback#error="
                                    + URLEncoder.encode(oAuthErrorMessage(exception), StandardCharsets.UTF_8));
                        }))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        (request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse("Authentication required")));
                        },
                        request -> request.getRequestURI().startsWith("/api/")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfHeaderFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private String oAuthErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Google sign-in failed. Check the backend logs for details.";
        }
        if (message.contains("authorization_request_not_found")) {
            return "Google sign-in session expired. Please start sign-in again.";
        }
        if (message.contains("invalid_client") || message.contains("Unauthorized")) {
            return "Google sign-in failed because the OAuth client secret is missing or invalid.";
        }
        return "Google sign-in failed: " + message;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", CsrfHeaderFilter.CSRF_HEADER));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
