package io.github.tawfikmetwally.payments.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class CreatePaymentRequestHasher {

    public String hash(CreatePaymentCommand command) {
        String payload = encodeField(Long.toString(command.amountMinor()))
                + encodeField(command.currency().getCurrencyCode())
                + encodeField(command.merchantReference())
                + encodeField(command.paymentMethodToken());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String encodeField(String value) {
        // Prefix each value with its length so field boundaries are unambiguous.
        return value.length() + ":" + value;
    }
}
