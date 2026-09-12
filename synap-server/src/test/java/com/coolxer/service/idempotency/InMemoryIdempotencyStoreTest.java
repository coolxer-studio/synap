package com.coolxer.service.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryIdempotencyStoreTest {
    @Test
    void claimsCompletesAndReleasesMessages() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 10);

        assertEquals(ClaimStatus.ACQUIRED, store.claim("first"));
        assertEquals(ClaimStatus.PROCESSING, store.claim("first"));
        store.complete("first");
        assertEquals(ClaimStatus.DONE, store.claim("first"));
        assertTrue(store.completedPayload("first").isEmpty());

        assertEquals(ClaimStatus.ACQUIRED, store.claim("response"));
        store.complete("response", "{\"status\":1}");
        assertEquals(ClaimStatus.DONE, store.claim("response"));
        assertEquals("{\"status\":1}", store.completedPayload("response").orElseThrow());

        assertEquals(ClaimStatus.ACQUIRED, store.claim("retry"));
        store.release("retry");
        assertEquals(ClaimStatus.ACQUIRED, store.claim("retry"));
    }
}
