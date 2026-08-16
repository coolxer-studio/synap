package com.coolxer.synap.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigurationTest {
    @Test void rejectsIncompleteSecureConfiguration() {
        AgentConfiguration value = AgentConfiguration.from(Map.of("server-url", "https://risk.example.com", "app-id", "1"));
        assertFalse(value.ready());
        assertEquals(AgentConfiguration.Mode.OBSERVE, value.mode());
    }

    @Test void permitsLoopbackHttpForDevelopment() {
        AgentConfiguration value = AgentConfiguration.from(Map.of("server-url", "http://127.0.0.1:11099", "app-id", "1",
                "app-key-id", "synap-server-dev", "server-key-id", "key", "trust-anchor", "value"));
        assertTrue(value.ready());
    }

    @Test void systemPropertiesOverrideSpringConfiguration() {
        System.setProperty("synap.agent.app-name", "system-name");
        try { assertEquals("system-name", AgentConfiguration.from(Map.of("app-name", "spring-name")).appName()); }
        finally { System.clearProperty("synap.agent.app-name"); }
    }
}
