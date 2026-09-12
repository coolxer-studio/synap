package com.coolxer.probe.demo.communication;

import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import okhttp3.HttpUrl;

public final class CommunicationConfigValidator {
    public enum Field {
        BASE_URL,
        APP_ID,
        SERVER_KEY_ID,
        TRUST_ANCHOR
    }

    private CommunicationConfigValidator() { }

    public static Result validate(CommunicationConfig config) {
        EnumMap<Field, String> errors = new EnumMap<>(Field.class);
        validateBaseUrl(config.getBaseUrl(), errors);
        validateAppId(config.getAppId(), errors);
        validateServerKeyId(config.getServerKeyId(), errors);
        validateTrustAnchor(config.getTrustAnchor(), errors);
        return new Result(errors);
    }

    private static void validateBaseUrl(String value, Map<Field, String> errors) {
        if (value.isEmpty()) {
            errors.put(Field.BASE_URL, "请输入服务地址");
            return;
        }
        HttpUrl parsed = HttpUrl.parse(value);
        if (parsed == null || !parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.query() != null || parsed.fragment() != null) {
            errors.put(Field.BASE_URL, "请输入不含用户信息、查询参数或片段的有效 URL");
            return;
        }
        if (!"https".equals(parsed.scheme()) && !"http".equals(parsed.scheme())) {
            errors.put(Field.BASE_URL, "服务地址必须使用 HTTP 或 HTTPS");
        }
    }

    private static void validateAppId(String value, Map<Field, String> errors) {
        if (value.isEmpty()) {
            errors.put(Field.APP_ID, "请输入 App ID");
        } else if (!value.matches("[A-Za-z0-9._:@/-]{1,128}")) {
            errors.put(Field.APP_ID, "App ID 格式无效");
        }
    }

    private static void validateServerKeyId(String value, Map<Field, String> errors) {
        if (value.isEmpty()) {
            errors.put(Field.SERVER_KEY_ID, "请输入服务端 Key ID");
        }
    }

    private static void validateTrustAnchor(String value, Map<Field, String> errors) {
        if (value.isEmpty()) {
            errors.put(Field.TRUST_ANCHOR, "请输入服务端信任锚");
            return;
        }
        try {
            byte[] encoded = decodeBase64Url(value);
            PublicKey publicKey = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            if (!(publicKey instanceof ECPublicKey)
                    || !usesP256((ECPublicKey) publicKey)) {
                errors.put(Field.TRUST_ANCHOR, "信任锚必须是 P-256 EC SPKI 公钥");
            }
        } catch (Exception exception) {
            errors.put(Field.TRUST_ANCHOR, "信任锚必须是有效的 Base64URL EC SPKI 公钥");
        }
    }

    private static boolean usesP256(ECPublicKey publicKey) throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class);
        ECParameterSpec actual = publicKey.getParams();
        return actual != null
                && expected.getCurve().equals(actual.getCurve())
                && expected.getGenerator().equals(actual.getGenerator())
                && expected.getOrder().equals(actual.getOrder())
                && expected.getCofactor() == actual.getCofactor();
    }

    private static byte[] decodeBase64Url(String value) {
        if (!value.matches("[A-Za-z0-9_-]+") || value.length() % 4 == 1) {
            throw new IllegalArgumentException("Invalid Base64URL");
        }
        byte[] output = new byte[value.length() * 6 / 8];
        int outputIndex = 0;
        int buffer = 0;
        int bitCount = 0;
        for (int index = 0; index < value.length(); index++) {
            buffer = (buffer << 6) | decodeCharacter(value.charAt(index));
            bitCount += 6;
            if (bitCount >= 8) {
                bitCount -= 8;
                output[outputIndex++] = (byte) ((buffer >> bitCount) & 0xff);
                buffer &= bitCount == 0 ? 0 : (1 << bitCount) - 1;
            }
        }
        if (buffer != 0) {
            throw new IllegalArgumentException("Non-canonical Base64URL");
        }
        if (outputIndex == output.length) return output;
        byte[] exact = new byte[outputIndex];
        System.arraycopy(output, 0, exact, 0, outputIndex);
        return exact;
    }

    private static int decodeCharacter(char value) {
        if (value >= 'A' && value <= 'Z') return value - 'A';
        if (value >= 'a' && value <= 'z') return value - 'a' + 26;
        if (value >= '0' && value <= '9') return value - '0' + 52;
        if (value == '-') return 62;
        if (value == '_') return 63;
        throw new IllegalArgumentException("Invalid Base64URL character");
    }

    public static final class Result {
        private final Map<Field, String> errors;

        private Result(EnumMap<Field, String> errors) {
            this.errors = Collections.unmodifiableMap(new EnumMap<>(errors));
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public String getError(Field field) {
            return errors.get(field);
        }

        public Map<Field, String> getErrors() {
            return errors;
        }
    }
}
