package io.github.tawfikmetwally.payments;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Temporary signing authority used only by tests; publishes no private key. */
public final class JwtSigningTestSupport implements AutoCloseable {

    public static final String ISSUER = "https://issuer.example.test/realms/payments";
    public static final String AUDIENCE = "payment-sandbox-api";
    private static final String KEY_ID = "test-key";
    private static final String ALL_SCOPES = "payments:create payments:read refunds:create";

    private final RSAKey signingKey;
    private final HttpServer server;

    public JwtSigningTestSupport() {
        try {
            signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] publicKeys = new JWKSet(signingKey.toPublicJWK())
                    .toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/certs", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, publicKeys.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(publicKeys);
                }
            });
            server.start();
        } catch (JOSEException | IOException exception) {
            throw new IllegalStateException("Cannot start test signing authority", exception);
        }
    }

    public void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/certs");
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
    }

    public String token(Object merchantId) throws JOSEException {
        return tokenWithScopes(merchantId, ALL_SCOPES);
    }

    public String tokenWithScopes(Object merchantId, String scopes) throws JOSEException {
        return sign(signingKey, merchantId, ISSUER, AUDIENCE, 300, scopes);
    }

    public String token(Object merchantId, String issuer, String audience, int expirySeconds)
            throws JOSEException {
        return sign(signingKey, merchantId, issuer, audience, expirySeconds, ALL_SCOPES);
    }

    public String tokenWithForeignSignature(String merchantId) throws JOSEException {
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        return sign(foreignKey, merchantId, ISSUER, AUDIENCE, 300, ALL_SCOPES);
    }

    private String sign(RSAKey key, Object merchantId, String issuer,
            String audience, int expirySeconds, String scopes) throws JOSEException {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("internal-service-account")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(expirySeconds)));
        if (scopes != null) {
            claims.claim("scope", scopes);
        }
        if (merchantId != null) {
            claims.claim("azp", merchantId);
        }
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
