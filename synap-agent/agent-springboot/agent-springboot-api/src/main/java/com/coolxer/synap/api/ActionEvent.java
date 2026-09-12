package com.coolxer.synap.api;

import java.util.Map;

public record ActionEvent(String id, String name, Phase phase, Map<String, ?> attributes) {
    public enum Phase { START, SUCCESS, FAILURE }
    public ActionEvent { attributes = attributes == null ? Map.of() : Map.copyOf(attributes); }
}
