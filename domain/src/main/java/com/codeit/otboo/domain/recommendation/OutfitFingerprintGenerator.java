package com.codeit.otboo.domain.recommendation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class OutfitFingerprintGenerator {

    private static final String SEPARATOR = ",";

    private OutfitFingerprintGenerator() {
    }

    public static String generate(Collection<UUID> clothesIds) {
        if (clothesIds == null || clothesIds.isEmpty() || clothesIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Clothes IDs must contain at least one non-null value.");
        }

        String canonicalValue = clothesIds.stream()
            .map(UUID::toString)
            .sorted()
            .reduce((left, right) -> left + SEPARATOR + right)
            .orElseThrow();

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }
}
