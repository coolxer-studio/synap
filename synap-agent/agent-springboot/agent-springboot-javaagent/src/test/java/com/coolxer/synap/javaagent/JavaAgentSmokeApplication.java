package com.coolxer.synap.javaagent;

import com.coolxer.synap.bridge.AgentBridge;

import java.util.List;
import java.util.Set;

public final class JavaAgentSmokeApplication {
    private JavaAgentSmokeApplication() {}

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getProperty("synap.javaagent.active"))) throw new IllegalStateException("premain was not called");
        AgentBridge.install(event -> {});
        AgentBridge.openRequest("asset", "GET", "/smoke", false, "ip-hmac", List.of("echo synap; true"));
        AgentBridge.configureProtection(true, Set.of("COMMAND_INJECTION_V1"));
        try {
            new ProcessBuilder("sh", "-c", "echo synap; true").start();
            throw new IllegalStateException("ProcessBuilder was not intercepted");
        } catch (SecurityException expected) {
            System.out.println("SYNAP_JAVAAGENT_INSTRUMENTED");
        } finally {
            AgentBridge.closeRequest();
        }
    }
}
