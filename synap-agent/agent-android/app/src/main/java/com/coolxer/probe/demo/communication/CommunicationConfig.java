package com.coolxer.probe.demo.communication;

import com.coolxer.securecommunication.SecureClientConfig;

import java.util.Collections;
import java.util.Objects;

public final class CommunicationConfig {
    private final String baseUrl;
    private final String appId;
    private final String serverKeyId;
    private final String trustAnchor;

    public CommunicationConfig(String baseUrl, String appId,
            String serverKeyId, String trustAnchor) {
        this.baseUrl = normalize(baseUrl);
        this.appId = normalize(appId);
        this.serverKeyId = normalize(serverKeyId);
        this.trustAnchor = normalize(trustAnchor);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public String getServerKeyId() {
        return serverKeyId;
    }

    public String getTrustAnchor() {
        return trustAnchor;
    }

    public SecureClientConfig toSecureClientConfig() {
        return SecureClientConfig.builder()
                .baseUrl(baseUrl)
                .appId(appId)
                .deviceType("ANDROID")
                .serverTrustAnchors(Collections.singletonMap(serverKeyId, trustAnchor))
                .build();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommunicationConfig)) return false;
        CommunicationConfig that = (CommunicationConfig) other;
        return baseUrl.equals(that.baseUrl)
                && appId.equals(that.appId)
                && serverKeyId.equals(that.serverKeyId)
                && trustAnchor.equals(that.trustAnchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUrl, appId, serverKeyId, trustAnchor);
    }
}
