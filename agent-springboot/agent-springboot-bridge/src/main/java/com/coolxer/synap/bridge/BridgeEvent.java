package com.coolxer.synap.bridge;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** A dependency-free event that may cross the bootstrap/application class-loader boundary. */
public record BridgeEvent(String type, long observedAt, Map<String, Object> data) {
    public BridgeEvent {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("event type is required");
        observedAt = observedAt > 0 ? observedAt : Instant.now().toEpochMilli();
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
    }

    public static BridgeEvent of(String type, Map<String, Object> data) {
        return new BridgeEvent(type, System.currentTimeMillis(), data);
    }
}
