package com.coolxer.service.idempotency;

import java.util.Optional;

public interface IdempotencyStore {
    ClaimStatus claim(String key);
    default void complete(String key) { complete(key, null); }
    void complete(String key, String completedPayload);
    Optional<String> completedPayload(String key);
    void release(String key);
}
