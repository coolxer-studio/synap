package com.coolxer.synap.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeAckTest {
    @Test
    void acceptsCamelAndSnakeCaseAckFields() {
        assertTrue(AgentRuntime.acknowledged(Map.of("acceptedMessageIds", List.of("m1")), "m1"));
        assertTrue(AgentRuntime.acknowledged(Map.of("duplicate_message_ids", List.of("m2")), "m2"));
        assertFalse(AgentRuntime.acknowledged(Map.of("accepted_message_ids", List.of("other")), "m1"));
    }
}
