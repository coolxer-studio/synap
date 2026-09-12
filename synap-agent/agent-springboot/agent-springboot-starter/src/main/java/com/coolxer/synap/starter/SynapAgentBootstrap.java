package com.coolxer.synap.starter;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;
import com.coolxer.synap.core.AgentConfiguration;
import com.coolxer.synap.core.AgentRuntime;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class SynapAgentBootstrap implements SmartLifecycle {
    private final SynapAgentProperties properties; private final Environment environment; private volatile boolean running;
    SynapAgentBootstrap(SynapAgentProperties properties, Environment environment) { this.properties = properties; this.environment = environment; }
    @Override public void start() {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("enabled", properties.isEnabled()); values.put("server-url", properties.getServerUrl());
        values.put("app-id", properties.getAppId()); values.put("app-name", properties.getAppName().isBlank() ? environment.getProperty("spring.application.name", "spring-boot-application") : properties.getAppName());
        values.put("app-version", properties.getAppVersion()); values.put("app-key-id", properties.getAppKeyId()); values.put("server-key-id", properties.getServerKeyId()); values.put("trust-anchor", properties.getTrustAnchor());
        if (!properties.getDataDir().isBlank()) values.put("data-dir", properties.getDataDir()); values.put("mode", properties.getMode()); values.put("protection.enabled-rule-ids", String.join(",", properties.getProtection().getEnabledRuleIds()));
        values.put("spool.max-items", properties.getSpool().getMaxItems()); values.put("spool.max-bytes", properties.getSpool().getMaxBytes()); values.put("api.observation-window", properties.getApi().getObservationWindow());
        String capability = System.getProperty("synap.javaagent.active", "false").equals("true") ? "full" : "degraded-no-javaagent";
        AgentRuntime.start(AgentConfiguration.from(values), capability);
        AgentBridge.emit(BridgeEvent.of("ApplicationData", Map.ofEntries(Map.entry("schemaVersion", 1),
                Map.entry("javaVersion", System.getProperty("java.version", "unknown")), Map.entry("javaVendor", System.getProperty("java.vendor", "unknown")),
                Map.entry("vmName", System.getProperty("java.vm.name", "unknown")), Map.entry("osName", System.getProperty("os.name", "unknown")),
                Map.entry("osArch", System.getProperty("os.arch", "unknown")), Map.entry("processors", Runtime.getRuntime().availableProcessors()),
                Map.entry("pid", ProcessHandle.current().pid()), Map.entry("capabilityMode", capability), Map.entry("inputArguments", java.util.List.of()),
                Map.entry("springBootVersion", String.valueOf(SpringBootVersion.getVersion())), Map.entry("springFrameworkVersion", String.valueOf(SpringVersion.getVersion())),
                Map.entry("appName", String.valueOf(values.get("app-name"))), Map.entry("appVersion", properties.getAppVersion()),
                Map.entry("profiles", Arrays.asList(environment.getActiveProfiles())))));
        AgentBridge.emit(BridgeEvent.of("SecurityConfigData", Map.of("schemaVersion", 1, "activeProfiles", Arrays.asList(environment.getActiveProfiles()),
                "managementEndpointsExposed", environment.getProperty("management.endpoints.web.exposure.include", ""),
                "csrfConfigured", environment.containsProperty("spring.security.enable-csrf"), "capabilityMode", capability)));
        running = true;
    }
    @Override public void stop() { running = false; }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MIN_VALUE + 100; }
}
