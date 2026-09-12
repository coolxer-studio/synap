package com.coolxer.synap.api;

import java.util.Map;

public record SecurityEvent(String category, String name, Severity severity, Map<String, ?> attributes) {
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public SecurityEvent { attributes = attributes == null ? Map.of() : Map.copyOf(attributes); }
}
