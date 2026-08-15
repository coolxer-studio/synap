package com.coolxer.probe.demo.communication;

import org.junit.Test;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommunicationConfigValidatorTest {
    @Test
    public void configTrimsEveryValue() {
        CommunicationConfig config = new CommunicationConfig(
                "  https://example.com  ", " synap-android ", " key-1 ", " anchor ");

        assertEquals("https://example.com", config.getBaseUrl());
        assertEquals("synap-android", config.getAppId());
        assertEquals("key-1", config.getServerKeyId());
        assertEquals("anchor", config.getTrustAnchor());
    }

    @Test
    public void validP256ConfigurationPasses() throws Exception {
        CommunicationConfigValidator.Result result = CommunicationConfigValidator.validate(
                config("https://example.com/api", "synap-android", "server-key", ecKey("secp256r1")));

        assertTrue(result.getErrors().toString(), result.isValid());
    }

    @Test
    public void baseUrlMustBeHttpsAndMustNotContainUnsafeComponents() throws Exception {
        String anchor = ecKey("secp256r1");
        assertFieldError(config("http://example.com", "synap-android", "key", anchor),
                CommunicationConfigValidator.Field.BASE_URL);
        assertFieldError(config("https://user@example.com", "synap-android", "key", anchor),
                CommunicationConfigValidator.Field.BASE_URL);
        assertFieldError(config("https://example.com/path?q=1", "synap-android", "key", anchor),
                CommunicationConfigValidator.Field.BASE_URL);
        assertFieldError(config("https://example.com/#fragment", "synap-android", "key", anchor),
                CommunicationConfigValidator.Field.BASE_URL);
    }

    @Test
    public void appIdUsesSecureClientRules() throws Exception {
        CommunicationConfig config = config("https://example.com", "invalid app id", "key",
                ecKey("secp256r1"));

        assertFieldError(config, CommunicationConfigValidator.Field.APP_ID);
    }

    @Test
    public void serverKeyIdIsRequired() throws Exception {
        CommunicationConfig config = config("https://example.com", "synap-android", "  ",
                ecKey("secp256r1"));

        assertFieldError(config, CommunicationConfigValidator.Field.SERVER_KEY_ID);
    }

    @Test
    public void trustAnchorMustBeCanonicalBase64Url() {
        assertFieldError(config("https://example.com", "synap-android", "key", "not+base64"),
                CommunicationConfigValidator.Field.TRUST_ANCHOR);
    }

    @Test
    public void trustAnchorMustBeEc() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String rsaKey = encode(generator.generateKeyPair().getPublic().getEncoded());

        assertFieldError(config("https://example.com", "synap-android", "key", rsaKey),
                CommunicationConfigValidator.Field.TRUST_ANCHOR);
    }

    @Test
    public void trustAnchorMustUseP256() throws Exception {
        CommunicationConfig config = config("https://example.com", "synap-android", "key",
                ecKey("secp384r1"));

        assertFieldError(config, CommunicationConfigValidator.Field.TRUST_ANCHOR);
    }

    @Test
    public void missingValuesReportEveryField() {
        CommunicationConfigValidator.Result result = CommunicationConfigValidator.validate(
                config("", "", "", ""));

        assertFalse(result.isValid());
        for (CommunicationConfigValidator.Field field : CommunicationConfigValidator.Field.values()) {
            assertNotNull(field.name(), result.getError(field));
        }
    }

    private static void assertFieldError(CommunicationConfig config,
            CommunicationConfigValidator.Field field) {
        CommunicationConfigValidator.Result result = CommunicationConfigValidator.validate(config);
        assertFalse(result.isValid());
        assertNotNull(result.getError(field));
    }

    private static CommunicationConfig config(String baseUrl, String appId,
            String serverKeyId, String trustAnchor) {
        return new CommunicationConfig(baseUrl, appId, serverKeyId, trustAnchor);
    }

    private static String ecKey(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return encode(generator.generateKeyPair().getPublic().getEncoded());
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
