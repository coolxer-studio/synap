package com.coolxer.model.fact.host;

import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostAuditFactTest {
    @Test
    void acceptsTheFixedAuditContract() {
        HostAuditFact fact = new HostAuditFact("AuditData", new Header("a-b-c-d-e-host-1-2.0.0"), new Common(), valid());
        assertEquals(1, fact.getEvents().size());
        assertEquals("d2719b37-9d9f-41a8-b1bc-9bdc6298c847", fact.getBatchId());
    }

    @Test
    void rejectsInvalidEnumsShapeAndDetailSize() {
        Map<String, Object> invalidCategory = valid();
        ((List<Object>) ((List<?>) invalidCategory.get("events")).get(0)).set(2, "unknown");
        assertThrows(IllegalArgumentException.class, () -> HostAuditFact.validate(invalidCategory));

        Map<String, Object> invalidShape = valid();
        ((List<?>) invalidShape.get("events")).clear();
        assertThrows(IllegalArgumentException.class, () -> HostAuditFact.validate(invalidShape));

        Map<String, Object> detail = valid();
        ((List<Object>) ((List<?>) detail.get("events")).get(0)).set(17, "x".repeat(4097));
        assertThrows(IllegalArgumentException.class, () -> HostAuditFact.validate(detail));
    }

    private static Map<String, Object> valid() {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("type", "AuditData");
        fact.put("buildDate", "2026-08-05 10:00:00");
        fact.put("batchId", "d2719b37-9d9f-41a8-b1bc-9bdc6298c847");
        fact.put("events", new ArrayList<>(List.of(new ArrayList<>(List.of(
                "bcd4f8a1d21d4395ad3c3815c01670aa", "2026-08-05T10:00:00Z", "session", "login", "failure",
                "alice", "alice", "sshd", "42", "sshd", "192.0.2.2:22", "198.51.100.8:50000", "tcp",
                "", "", "auditd", "boot-id", "{}")))));
        return fact;
    }
}
