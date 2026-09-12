package com.coolxer.synap.starter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiAssetSupportTest {
    @Test void normalizesIdentifiersAndKeepsStableAssetId() {
        assertEquals("/users/{id}", ApiAssetSupport.normalizeRoute("/users/123456"));
        assertEquals(ApiAssetSupport.assetId(1, "GET", "/users/{id}"), ApiAssetSupport.assetId(1, "GET", "/users/123456"));
    }
    @Test void neverIncludesRawIpInHash() { assertFalse(ApiAssetSupport.dailyIpHash("192.0.2.1").contains("192.0.2.1")); }
}
