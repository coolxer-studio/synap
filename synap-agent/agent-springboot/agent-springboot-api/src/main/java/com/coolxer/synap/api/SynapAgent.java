package com.coolxer.synap.api;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SynapAgent {
    private static final int MAX_USER_ID = 256;
    private static final int MAX_ATTRIBUTES = 32 * 1024;
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|passwd|pwd|token|secret|authorization|cookie|card|cvv|idnumber|credential).*");

    private SynapAgent() {}

    public static boolean setUser(String opaqueUserId, Map<String, ?> attributes) {
        if (opaqueUserId == null || opaqueUserId.isBlank() || opaqueUserId.length() > MAX_USER_ID || !valid(attributes)) return false;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1); data.put("userId", opaqueUserId); data.put("attributes", attributes == null ? Map.of() : attributes);
        AgentBridge.emit(BridgeEvent.of("UserData", data));
        return true;
    }

    public static boolean markAction(ActionEvent event) {
        if (event == null || blank(event.id()) || blank(event.name()) || event.phase() == null || !valid(event.attributes())) return false;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1); data.put("actionId", event.id()); data.put("name", event.name());
        data.put("phase", event.phase().name().toLowerCase(Locale.ROOT)); data.put("attributes", event.attributes());
        AgentBridge.emit(BridgeEvent.of("ActionData", data));
        return true;
    }

    public static boolean reportSecurityEvent(SecurityEvent event) {
        if (event == null || blank(event.category()) || blank(event.name()) || event.severity() == null || !valid(event.attributes())) return false;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1); data.put("category", event.category()); data.put("name", event.name());
        data.put("severity", event.severity().name().toLowerCase(Locale.ROOT)); data.put("attributes", event.attributes());
        AgentBridge.emit(BridgeEvent.of("SecurityEventData", data));
        return true;
    }

    public static void flush() { AgentBridge.flush(); }

    public static AgentHealth health() {
        Map<String, Object> values = AgentBridge.health();
        return new AgentHealth(String.valueOf(values.getOrDefault("status", "degraded")), values);
    }

    private static boolean valid(Map<String, ?> attributes) {
        if (attributes == null) return true;
        if (attributes.size() > 64) return false;
        int size = 0;
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            if (entry.getKey() == null || SENSITIVE.matcher(entry.getKey()).matches()) return false;
            size += entry.getKey().length() + String.valueOf(entry.getValue()).length();
            if (size > MAX_ATTRIBUTES) return false;
        }
        return true;
    }

    private static boolean blank(String value) { return value == null || value.isBlank() || value.length() > 256; }
}
