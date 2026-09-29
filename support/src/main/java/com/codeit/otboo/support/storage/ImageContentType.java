package com.codeit.otboo.support.storage;

import org.apache.tika.Tika;

import java.util.Locale;

public final class ImageContentType {

    private static final Tika TIKA = new Tika();

    private ImageContentType() {
    }

    /** Detects the file format, not whether the entire image can be decoded. */
    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Image content is required");
        }
        // Do not pass a filename: its extension must not override the actual content.
        String detected = TIKA.detect(bytes);
        if (!detected.startsWith("image/")) {
            throw new IllegalArgumentException("Cannot identify image content type: " + detected);
        }
        return detected;
    }

    public static String resolve(byte[] bytes, String contentType) {
        String normalized = contentType == null ? ""
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("application/octet-stream")
                || normalized.equals("binary/octet-stream")) {
            return detect(bytes);
        }
        return normalized.equals("image/jpg") ? "image/jpeg" : normalized;
    }
}
