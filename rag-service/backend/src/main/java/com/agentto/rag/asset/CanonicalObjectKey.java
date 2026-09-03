package com.agentto.rag.asset;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CanonicalObjectKey {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private CanonicalObjectKey() {
    }

    public static String of(String sha256) {
        if (sha256 == null) {
            throw new IllegalArgumentException("sha256 不能为空");
        }
        String hex = sha256.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(hex).matches()) {
            throw new IllegalArgumentException("sha256 必须是 64 位十六进制");
        }
        return "content-assets/sha256/%s/%s".formatted(hex.substring(0, 2), hex);
    }
}
