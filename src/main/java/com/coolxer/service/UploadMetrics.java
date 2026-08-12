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
    private final LongAdder startTracked = new LongAdder();
    private final LongAdder startLegacy = new LongAdder();
    private final LongAdder startDuplicates = new LongAdder();
    private final LongAdder startRejected = new LongAdder();
    private final LongAdder startProcessing = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> facts = new ConcurrentHashMap<>();

    public void batch() { batches.increment(); }
    public void accepted(String type) {
        accepted.increment();
        facts.computeIfAbsent(type == null ? "unknown" : type, ignored -> new LongAdder()).increment();
    }
    public void duplicate() { duplicates.increment(); }
    public void rejected() { rejected.increment(); }
    public void kafkaFailure() { kafkaFailures.increment(); }
    public void startTracked() { startTracked.increment(); }
    public void startLegacy() { startLegacy.increment(); }
    public void startDuplicate() { startDuplicates.increment(); }
    public void startRejected() { startRejected.increment(); }
    public void startProcessing() { startProcessing.increment(); }
    public Map<String, Object> snapshot() {
        Map<String, Long> factCounts = new ConcurrentHashMap<>();
        facts.forEach((key, value) -> factCounts.put(key, value.sum()));
        Map<String, Object> result = new ConcurrentHashMap<>();
        result.put("batches", batches.sum()); result.put("accepted", accepted.sum());
        result.put("duplicates", duplicates.sum()); result.put("rejected", rejected.sum());
        result.put("kafkaFailures", kafkaFailures.sum()); result.put("facts", factCounts);
        result.put("startTracked", startTracked.sum()); result.put("startLegacy", startLegacy.sum());
        result.put("startDuplicates", startDuplicates.sum()); result.put("startRejected", startRejected.sum());
        result.put("startProcessing", startProcessing.sum());
        return result;
    }
}
