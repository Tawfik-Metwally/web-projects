package io.github.tawfikmetwally.payments;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class JwtTestAuthentication {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-14T12:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(300);

    private JwtTestAuthentication() {
    }

    public static RequestPostProcessor merchantJwt(String merchantId) {
        Jwt jwt = Jwt.withTokenValue("test-token-" + merchantId)
                .header("alg", "none")
                .subject("service-account-" + merchantId)
                .claim("azp", merchantId)
                .claim(
                        "scope",
                        "payments:create payments:read refunds:create")
                .issuedAt(ISSUED_AT)
                .expiresAt(EXPIRES_AT)
                .build();
        return authentication(new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("SCOPE_payments:create"),
                        new SimpleGrantedAuthority("SCOPE_payments:read"),
                        new SimpleGrantedAuthority("SCOPE_refunds:create")),
                merchantId));
    }
}
