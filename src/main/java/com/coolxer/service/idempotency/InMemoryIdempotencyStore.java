package com.coolxer.service.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "synap.idempotency.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryIdempotencyStore implements IdempotencyStore {
    private static final String PROCESSING = "processing";
    private static final String DONE = "done";

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long processingTtlMillis;
    private final long doneTtlMillis;
    private final int maxEntries;

    @Autowired
    public InMemoryIdempotencyStore(
            @Value("${synap.idempotency.processing-ttl:PT5M}") Duration processingTtl,
            @Value("${synap.idempotency.done-ttl:P14D}") Duration doneTtl,
            @Value("${synap.idempotency.memory.max-entries:100000}") int maxEntries) {
        this(Clock.systemUTC(), processingTtl, doneTtl, maxEntries);
    }

    InMemoryIdempotencyStore(Clock clock, Duration processingTtl, Duration doneTtl, int maxEntries) {
        this.clock = clock;
        this.processingTtlMillis = processingTtl.toMillis();
        this.doneTtlMillis = doneTtl.toMillis();
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized ClaimStatus claim(String key) {
        evictExpired();
        Entry current = entries.get(key);
        if (current != null) return DONE.equals(current.state) ? ClaimStatus.DONE : ClaimStatus.PROCESSING;
        if (entries.size() >= maxEntries) {
            entries.entrySet().stream().min(Comparator.comparingLong(entry -> entry.getValue().expiresAt))
                    .ifPresent(entry -> entries.remove(entry.getKey(), entry.getValue()));
        }
        entries.put(key, new Entry(PROCESSING, clock.millis() + processingTtlMillis));
        return ClaimStatus.ACQUIRED;
    }

    @Override
    public synchronized void complete(String key) {
        entries.put(key, new Entry(DONE, clock.millis() + doneTtlMillis));
    }

    @Override
    public synchronized void release(String key) {
        Entry current = entries.get(key);
        if (current != null && PROCESSING.equals(current.state)) entries.remove(key, current);
    }

    private void evictExpired() {
        long now = clock.millis();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private record Entry(String state, long expiresAt) { }
}
