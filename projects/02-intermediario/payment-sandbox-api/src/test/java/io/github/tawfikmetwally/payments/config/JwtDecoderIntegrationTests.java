package io.github.tawfikmetwally.payments.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tawfikmetwally.payments.JwtSigningTestSupport;

@WebMvcTest(JwtDecoderIntegrationTests.Probe.class)
@Import(SecurityConfiguration.class)
class JwtDecoderIntegrationTests {

    private static final JwtSigningTestSupport SIGNING = new JwtSigningTestSupport();

    @Autowired
    private JwtDecoder decoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SIGNING.registerProperties(registry);
    }

    @AfterAll
    static void stopServer() {
        SIGNING.close();
    }

    @Test
    void acceptsSignedTokenForExpectedIssuerAndAudience() throws Exception {
        assertThat(decoder.decode(SIGNING.token("merchant-a-client")).getSubject())
                .isEqualTo("internal-service-account");
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        reject(SIGNING.token("merchant-a-client", "https://other.example.test",
                JwtSigningTestSupport.AUDIENCE, 300));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        reject(SIGNING.token("merchant-a-client", JwtSigningTestSupport.ISSUER,
                "other-api", 300));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        reject(SIGNING.token("merchant-a-client", JwtSigningTestSupport.ISSUER,
                JwtSigningTestSupport.AUDIENCE, -300));
    }

    @Test
    void rejectsForeignSignature() throws Exception {
        reject(SIGNING.tokenWithForeignSignature("merchant-a-client"));
    }

    private void reject(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @RestController
    static class Probe {
        @GetMapping("/api/probe")
        String probe() {
            return "ok";
        }
    }
}
