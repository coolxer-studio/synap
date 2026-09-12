package com.coolxer.synap.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigurationTest {
    @Test void rejectsIncompleteSecureConfiguration() {
        AgentConfiguration value = AgentConfiguration.from(Map.of("server-url", "https://risk.example.com", "app-id", "1"));
        assertFalse(value.ready());
        assertEquals(java.util.List.of("app-key-id/SYNAP_SC_APP_ID", "server-key-id/SYNAP_SC_SERVER_KEY_ID",
                "trust-anchor/SYNAP_SC_TRUST_ANCHOR/SYNAP_SC_SERVER_PUBLIC_KEY"), value.missingRequiredSettings());
        assertEquals(AgentConfiguration.Mode.OBSERVE, value.mode());
    }

    @Test void permitsRemoteHttp() {
        AgentConfiguration value = AgentConfiguration.from(Map.of("server-url", "http://risk.example.com:11099", "app-id", "1",
                "app-key-id", "synap-server-dev", "server-key-id", "key", "trust-anchor", "value"));
        assertTrue(value.ready());
    }

    @Test void systemPropertiesOverrideSpringConfiguration() {
        System.setProperty("synap.agent.app-name", "system-name");
        try { assertEquals("system-name", AgentConfiguration.from(Map.of("app-name", "spring-name")).appName()); }
        finally { System.clearProperty("synap.agent.app-name"); }
    }
}
