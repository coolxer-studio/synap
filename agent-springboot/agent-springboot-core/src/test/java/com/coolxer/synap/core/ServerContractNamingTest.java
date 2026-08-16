package com.coolxer.synap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerContractNamingTest {
    @TempDir Path dataDir;

    @Test
    void offlineSpoolUsesServerPlatformAndGenericRuntimeFact() throws Exception {
        AgentConfiguration configuration = AgentConfiguration.from(Map.ofEntries(
                Map.entry("server-url", "http://127.0.0.1:1"), Map.entry("app-id", "1"),
                Map.entry("app-name", "server-contract-test"), Map.entry("app-key-id", "server-test"),
                Map.entry("server-key-id", "test-key"), Map.entry("trust-anchor", "AA"),
                Map.entry("data-dir", dataDir.toString()), Map.entry("api.observation-window", "1")));
        AgentRuntime runtime = AgentRuntime.start(configuration, "full");
        assertTrue(runtime != null);
        try {
            long deadline = System.currentTimeMillis() + 4_000;
            boolean runtimeFactFound = false;
            while (System.currentTimeMillis() < deadline && !runtimeFactFound) {
                try (var files = Files.list(dataDir.resolve("spool"))) {
                    for (Path path : files.toList()) {
                        Map<String, Object> envelope = JsonSupport.map(Files.readAllBytes(path));
                        @SuppressWarnings("unchecked") List<String> common = (List<String>) envelope.get("common");
                        assertEquals("server", common.get(8));
                        @SuppressWarnings("unchecked") List<Map<String, Object>> facts = (List<Map<String, Object>>) envelope.get("data");
                        runtimeFactFound |= facts.stream().anyMatch(fact -> "RuntimeData".equals(fact.get("type")));
                        assertFalse(facts.stream().anyMatch(fact -> "JvmRuntimeData".equals(fact.get("type"))));
                    }
                }
                if (!runtimeFactFound) Thread.sleep(100);
            }
            assertTrue(runtimeFactFound, "RuntimeData was not written to the offline spool");
        } finally {
            runtime.close();
        }
    }
}
