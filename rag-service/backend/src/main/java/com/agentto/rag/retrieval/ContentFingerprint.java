package com.agentto.rag.retrieval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class ContentFingerprint {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ContentFingerprint() {
    }

    public static String sha256(String content) {
        String normalized = WHITESPACE.matcher(Normalizer.normalize(
                content == null ? "" : content, Normalizer.Form.NFKC)).replaceAll(" ").trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
