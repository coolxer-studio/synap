package com.coolxer.synap.api;

import java.util.Map;

public record AgentHealth(String status, Map<String, Object> details) {}
