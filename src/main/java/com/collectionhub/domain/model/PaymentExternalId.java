
package com.collectionhub.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PaymentExternalId {

    private PaymentExternalId() {
    }

    public static String fromIdempotencyKey(String key) {

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key obligatorio"
            );
        }

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));

            return "CH-" + HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 no está disponible", ex
            );
        }
    }
}
