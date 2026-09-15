package io.github.tawfikmetwally.payments.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.*;

@WebMvcTest(JwtDecoderIntegrationTests.Probe.class)
@Import(SecurityConfiguration.class)
class JwtDecoderIntegrationTests {
    private static final RSAKey KEY;
    private static final HttpServer SERVER;
    private static final String ISSUER = "https://issuer.example.test/realms/payments";
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/certs", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            SERVER.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @Autowired JwtDecoder decoder;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/certs");
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "payment-sandbox-api");
    }
    @AfterAll static void stopServer() { SERVER.stop(0); }
    @Test void acceptsSignedTokenForExpectedIssuerAndAudience() throws Exception {
        assertThat(decoder.decode(token(KEY, ISSUER, "payment-sandbox-api", 300)).getSubject())
                .isEqualTo("service-account");
    }
    @Test void rejectsWrongIssuer() throws Exception {
        reject(token(KEY, "https://other.example.test", "payment-sandbox-api", 300));
    }
    @Test void rejectsWrongAudience() throws Exception {
        reject(token(KEY, ISSUER, "other-api", 300));
    }
    @Test void rejectsExpiredToken() throws Exception {
        reject(token(KEY, ISSUER, "payment-sandbox-api", -300));
    }
    @Test void rejectsForeignSignature() throws Exception {
        reject(token(new RSAKeyGenerator(2048).keyID("test-key").generate(),
                ISSUER, "payment-sandbox-api", 300));
    }
    private void reject(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    private String token(RSAKey key, String issuer, String audience, int expiry) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("service-account")
                .audience(List.of(audience)).claim("azp", "merchant-a-client")
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(expiry))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    @RestController static class Probe {
        @GetMapping("/api/probe") String probe() { return "ok"; }
    }
}
