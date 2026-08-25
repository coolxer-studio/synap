package com.coolxer.synap.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void sinkEvaluationDoesNotObserveItsOwnSinkIo() {
        AtomicInteger nestedCalls = new AtomicInteger();
        AgentBridge.install(event -> {
            events.add(event);
            nestedCalls.incrementAndGet();
            AgentBridge.beforeSink("FILE", "java.nio.file.Files.write",
                    new Object[]{"agent-spool.json"});
        });
        AgentBridge.openRequest("asset", "POST", "/demo", true, "ip-hmac", List.of("input"));

        assertEquals(AgentBridge.Decision.ALLOW,
                AgentBridge.beforeSink("COMMAND", "ProcessBuilder.start", new Object[]{"echo input"}));

        assertEquals(1, nestedCalls.get());
        assertEquals(1, events.size());
        assertEquals("COMMAND", events.get(0).data().get("sinkType"));
    }

    @Test
    void internalAgentOperationsAreNotObserved() {
        AgentBridge.install(events::add);
        AgentBridge.openRequest("asset", "POST", "/demo", true, "ip-hmac", List.of("input"));

        AgentBridge.enterInternalOperation();
        try {
            AgentBridge.enterInternalOperation();
            try {
                assertEquals(AgentBridge.Decision.ALLOW,
                        AgentBridge.beforeSink("FILE", "java.nio.file.Files.write",
                                new Object[]{"agent-spool.json"}));
            } finally {
                AgentBridge.exitInternalOperation();
            }
            assertEquals(AgentBridge.Decision.ALLOW,
                    AgentBridge.beforeSink("HTTP", "HttpClient.send",
                            new Object[]{"http://localhost:11099"}));
            assertEquals(0, events.size());
        } finally {
            AgentBridge.exitInternalOperation();
        }

        AgentBridge.beforeSink("FILE", "java.nio.file.Files.write", new Object[]{"business-file"});
        assertEquals(1, events.size());
    }
}
