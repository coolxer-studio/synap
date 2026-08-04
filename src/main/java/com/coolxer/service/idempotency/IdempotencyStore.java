package com.coolxer.service.idempotency;

public interface IdempotencyStore {
    ClaimStatus claim(String key);
    void complete(String key);
    void release(String key);
}
