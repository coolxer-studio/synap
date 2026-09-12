package com.coolxer.model.fact.server;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Base for versioned, named server application facts. */
@Getter
public abstract class ServerStructuredFact extends AbstractFact {
    private static final Set<String> TRANSPORT = Set.of("type", "buildDate", "messageId", "observedAt", "sequence");
    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("ApplicationData", Set.of("schemaVersion", "javaVersion", "javaVendor", "vmName", "osName", "osArch", "processors", "pid", "capabilityMode", "inputArguments", "startTime", "runtimeAgents", "cgroup", "springBootVersion", "springFrameworkVersion", "appName", "appVersion", "profiles")),
            Map.entry("RuntimeData", Set.of("schemaVersion", "heapUsed", "heapCommitted", "nonHeapUsed", "threadCount", "peakThreadCount", "deadlockedThreadCount", "loadedClassCount", "totalLoadedClassCount", "uptimeMs", "garbageCollectors", "daemonThreadCount", "unloadedClassCount", "systemLoadAverage", "processCpuLoad", "systemCpuLoad", "openFileDescriptorCount", "maxFileDescriptorCount")),
            Map.entry("ApiAssetData", Set.of("schemaVersion", "assets")),
            Map.entry("ApiObservationData", Set.of("schemaVersion", "windowStart", "windowEnd", "observations")),
            Map.entry("SecurityConfigData", Set.of("schemaVersion", "activeProfiles", "managementEndpointsExposed", "csrfConfigured", "corsConfigured", "sessionConfigured", "anonymousEnabled", "authenticationModes", "filterChains", "capabilityMode")),
            Map.entry("DependencyData", Set.of("schemaVersion", "dependencies")),
            Map.entry("DangerousCallData", Set.of("schemaVersion", "sinkType", "operation", "targetDigest", "stackFingerprint", "correlated", "confidence", "ruleId", "action", "correlationId", "assetId", "authenticated", "sourceIpHash")),
            Map.entry("SecurityEventData", Set.of("schemaVersion", "category", "name", "severity", "attributes", "assetId")),
            Map.entry("AgentHealthData", Set.of("schemaVersion", "status", "capabilityMode", "queued", "deadLetters", "accepted", "rejected", "failures", "dropped")),
            Map.entry("UserData", Set.of("schemaVersion", "userId", "attributes")),
            Map.entry("ActionData", Set.of("schemaVersion", "actionId", "name", "phase", "attributes")));

    private final Map<String, Object> payload;

    protected ServerStructuredFact(String type, Header header, Common common, Map<String, Object> factMap) {
        super(type, String.valueOf(factMap == null ? "" : factMap.get("buildDate")), header, common);
        validate(type, factMap);
        LinkedHashMap<String, Object> value = new LinkedHashMap<>(factMap);
        TRANSPORT.forEach(value::remove);
        this.payload = Map.copyOf(value);
    }

    public static void validate(String type, Map<String, Object> factMap) {
        Set<String> allowed = FIELDS.get(type);
        if (allowed == null || factMap == null || !type.equals(factMap.get("type"))) throw new IllegalArgumentException("invalid server fact type");
        if (!(factMap.get("schemaVersion") instanceof Number version) || version.intValue() != 1) throw new IllegalArgumentException("unsupported server fact schemaVersion");
        for (String key : factMap.keySet()) if (!TRANSPORT.contains(key) && !allowed.contains(key)) throw new IllegalArgumentException("unknown server fact field: " + key);
        if (!valid(factMap, 0)) throw new IllegalArgumentException("server fact exceeds structural limits");
    }

    private static boolean valid(Object value, int depth) {
        if (depth > 7) return false;
        if (value == null || value instanceof Number || value instanceof Boolean) return true;
        if (value instanceof String text) return text.length() <= 8192;
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 128) return false;
            for (Map.Entry<?, ?> entry : map.entrySet()) if (!(entry.getKey() instanceof String key) || key.length() > 128 || !valid(entry.getValue(), depth + 1)) return false;
            return true;
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > 2100) return false;
            for (Object item : collection) if (!valid(item, depth + 1)) return false;
            return true;
        }
        return false;
    }
}
