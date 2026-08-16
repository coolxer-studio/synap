package com.coolxer.synap.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentBridgeTest {
    private final List<BridgeEvent> events = new ArrayList<>();

    @AfterEach
    void cleanup() {
        AgentBridge.closeRequest();
        AgentBridge.configureProtection(false, Set.of());
    }

    @Test
    void observeModeCorrelatesWithoutLeakingTheInput() {
        AgentBridge.install(events::add);
        String canary = "synap-canary-secret";
        AgentBridge.openRequest("asset", "GET", "/demo/{id}", true, "ip-hmac", List.of(canary));

        assertEquals(AgentBridge.Decision.ALLOW,
                AgentBridge.beforeSink("SQL", "execute", new Object[]{"select * from t where v='" + canary + "'"}));

        BridgeEvent event = events.get(events.size() - 1);
        assertEquals("DangerousCallData", event.type());
        assertEquals(true, event.data().get("correlated"));
        assertFalse(event.data().toString().contains(canary));
    }

    @Test
    void protectModeOnlyBlocksAnEnabledBuiltInRule() {
        AgentBridge.install(events::add);
        AgentBridge.openRequest("asset", "GET", "/demo", false, "ip-hmac", List.of("whoami; id"));
        AgentBridge.configureProtection(true, Set.of("COMMAND_INJECTION_V1"));
        assertEquals(AgentBridge.Decision.BLOCK,
                AgentBridge.beforeSink("COMMAND", "Runtime.exec", new Object[]{"sh -c whoami; id"}));

        AgentBridge.configureProtection(true, Set.of("PATH_TRAVERSAL_V1"));
        assertEquals(AgentBridge.Decision.ALLOW,
                AgentBridge.beforeSink("COMMAND", "Runtime.exec", new Object[]{"sh -c whoami; id"}));
    }

    @Test
    void sinkWithoutRequestContextIsFailOpenAndNotRecorded() {
        AgentBridge.install(events::add);
        int before = events.size();
        assertEquals(AgentBridge.Decision.ALLOW,
                AgentBridge.beforeSink("COMMAND", "Runtime.exec", new Object[]{"echo ok"}));
        assertEquals(before, events.size());
    }
}
