package io.github.tawfikmetwally.payments.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class CreateRefundRequestHasher {

    public String hash(CreateRefundCommand command) {
        String payload = encodeField(command.paymentId().toString())
                + encodeField(command.reason());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String encodeField(String value) {
        return value.length() + ":" + value;
    }
}
