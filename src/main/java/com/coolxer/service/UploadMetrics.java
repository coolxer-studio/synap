package com.coolxer.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class UploadMetrics {
    private final LongAdder batches = new LongAdder();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder duplicates = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder kafkaFailures = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> facts = new ConcurrentHashMap<>();

    public void batch() { batches.increment(); }
    public void accepted(String type) {
        accepted.increment();
        facts.computeIfAbsent(type == null ? "unknown" : type, ignored -> new LongAdder()).increment();
    }
    public void duplicate() { duplicates.increment(); }
    public void rejected() { rejected.increment(); }
    public void kafkaFailure() { kafkaFailures.increment(); }
    public Map<String, Object> snapshot() {
        Map<String, Long> factCounts = new ConcurrentHashMap<>();
        facts.forEach((key, value) -> factCounts.put(key, value.sum()));
        return Map.of("batches", batches.sum(), "accepted", accepted.sum(),
                "duplicates", duplicates.sum(), "rejected", rejected.sum(),
                "kafkaFailures", kafkaFailures.sum(), "facts", factCounts);
    }
}
