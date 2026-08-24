package com.coolxer.synap.core;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record AgentConfiguration(
        boolean enabled, URI serverUrl, int appId, String appName, String appVersion,
        String appKeyId, String serverKeyId, String trustAnchor, String enrollmentToken,
        Path dataDir, Mode mode, Set<String> enabledRuleIds,
        int spoolMaxItems, long spoolMaxBytes, int observationWindowSeconds) {

    public enum Mode { OBSERVE, PROTECT }
    public static final String VERSION = "1.0.0";

    public AgentConfiguration {
        appName = blankDefault(appName, "spring-boot-application");
        appVersion = blankDefault(appVersion, "unknown");
        appKeyId = trim(appKeyId); serverKeyId = trim(serverKeyId); trustAnchor = trim(trustAnchor);
        enrollmentToken = trim(enrollmentToken);
        dataDir = dataDir == null ? Path.of(System.getProperty("user.home", "."), ".synap", "agent-springboot", appName) : dataDir;
        mode = mode == null ? Mode.OBSERVE : mode;
        enabledRuleIds = enabledRuleIds == null ? Set.of() : Set.copyOf(enabledRuleIds);
        spoolMaxItems = spoolMaxItems <= 0 ? 10_000 : spoolMaxItems;
        spoolMaxBytes = spoolMaxBytes <= 0 ? 100L * 1024 * 1024 : spoolMaxBytes;
        observationWindowSeconds = observationWindowSeconds <= 0 ? 60 : observationWindowSeconds;
    }

    public boolean ready() {
        if (!enabled || serverUrl == null || appId <= 0 || appKeyId.isBlank() || serverKeyId.isBlank() || trustAnchor.isBlank()) return false;
        String scheme = serverUrl.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    public static AgentConfiguration fromEnvironment() {
        return from(Map.of());
    }

    public static AgentConfiguration from(Map<String, ?> properties) {
        String server = value(properties, "server-url", "");
        String applicationName = value(properties, "app-name", System.getProperty("spring.application.name", "spring-boot-application"));
        return new AgentConfiguration(
                bool(value(properties, "enabled", "true")),
                server.isBlank() ? null : URI.create(server),
                integer(value(properties, "app-id", "0"), 0),
                applicationName,
                value(properties, "app-version", "unknown"),
                value(properties, "app-key-id", ""),
                value(properties, "server-key-id", ""),
                value(properties, "trust-anchor", env("SYNAP_SC_SERVER_PUBLIC_KEY", "")),
                env("SYNAP_SC_ENROLLMENT_TOKEN", ""),
                Path.of(value(properties, "data-dir", Path.of(System.getProperty("user.home", "."), ".synap", "agent-springboot", applicationName).toString())),
                "protect".equalsIgnoreCase(value(properties, "mode", "observe")) ? Mode.PROTECT : Mode.OBSERVE,
                csv(value(properties, "protection.enabled-rule-ids", "")),
                integer(value(properties, "spool.max-items", "10000"), 10_000),
                longValue(value(properties, "spool.max-bytes", "104857600"), 100L * 1024 * 1024),
                integer(value(properties, "api.observation-window", "60"), 60));
    }

    private static String value(Map<String, ?> values, String key, String fallback) {
        String system = System.getProperty("synap.agent." + key);
        if (system != null && !system.isBlank()) return system.trim();
        String environmentName = switch (key) {
            case "app-key-id" -> "SYNAP_SC_APP_ID"; case "server-key-id" -> "SYNAP_SC_SERVER_KEY_ID";
            case "trust-anchor" -> "SYNAP_SC_TRUST_ANCHOR"; case "server-url" -> "SYNAP_AGENT_SERVER_URL";
            case "protection.enabled-rule-ids" -> "SYNAP_AGENT_PROTECTION_RULES";
            default -> "SYNAP_AGENT_" + key.toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        };
        String external = env(environmentName, ""); if (!external.isBlank()) return external;
        Object value = values.get(key); return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }
    private static String env(String name, String fallback) {
        String property = System.getProperty(name.toLowerCase(Locale.ROOT).replace('_', '.'));
        if (property != null && !property.isBlank()) return property.trim();
        String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value.trim();
    }
    private static Set<String> csv(String value) { return value == null || value.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(value.split("\\s*,\\s*"))); }
    private static boolean bool(String value) { return Boolean.parseBoolean(value); }
    private static int integer(String value, int fallback) { try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return fallback; } }
    private static long longValue(String value, long fallback) { try { return Long.parseLong(value); } catch (RuntimeException ignored) { return fallback; } }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String blankDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
