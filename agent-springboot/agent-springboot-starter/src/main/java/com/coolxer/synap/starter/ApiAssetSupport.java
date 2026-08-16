package com.coolxer.synap.starter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ApiAssetSupport {
    private static final Pattern PATH_VALUE = Pattern.compile("/(?:[0-9]{2,}|[0-9a-fA-F]{8,}|[0-9a-fA-F-]{36})(?=/|$)");
    private static final byte[] PROCESS_HMAC_KEY = new byte[32];
    static { new SecureRandom().nextBytes(PROCESS_HMAC_KEY); }
    private ApiAssetSupport() {}
    public static String normalizeRoute(String route) {
        if (route == null || route.isBlank()) return "/dynamic/unmapped";
        String value = route.split("\\?", 2)[0].replaceAll("/+", "/");
        value = PATH_VALUE.matcher(value).replaceAll("/{id}");
        return value.length() > 512 ? value.substring(0, 512) : value;
    }
    public static String assetId(int appId, String method, String route) { return sha256(appId + "\n" + method.toUpperCase(Locale.ROOT) + "\n" + normalizeRoute(route)); }
    public static String dailyIpHash(String value) { return value == null || value.isBlank() ? "" : dailyHmac(value); }
    public static String dailyHmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(PROCESS_HMAC_KEY, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((LocalDate.now(ZoneOffset.UTC) + "\n" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
