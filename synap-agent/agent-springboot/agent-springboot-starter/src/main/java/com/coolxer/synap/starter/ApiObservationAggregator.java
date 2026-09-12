package com.coolxer.synap.starter;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

final class ApiObservationAggregator implements AutoCloseable {
    private static final class Stat {
        final LongAdder count = new LongAdder(), denied = new LongAdder(), unauthenticated = new LongAdder(), totalLatency = new LongAdder();
        final LongAdder requestBytes = new LongAdder(), responseBytes = new LongAdder();
        volatile String contentType = "";
        final LongAdder[] statuses = {new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()};
        final LongAdder[] latency = {new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()};
    }
    private final ConcurrentHashMap<String, Stat> values = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final int maxAssets;
    private volatile long windowStart = System.currentTimeMillis();

    ApiObservationAggregator(int windowSeconds, int maxAssets) {
        this.maxAssets = maxAssets;
        executor = Executors.newSingleThreadScheduledExecutor(task -> { Thread thread = new Thread(task, "synap-api-observations"); thread.setDaemon(true); return thread; });
        executor.scheduleWithFixedDelay(this::flush, windowSeconds, windowSeconds, TimeUnit.SECONDS);
    }
    void record(String assetId, int status, long latencyMs, boolean authenticated, long requestBytes, long responseBytes, String contentType) {
        String key = values.size() >= maxAssets && !values.containsKey(assetId) ? "overflow" : assetId;
        Stat stat = values.computeIfAbsent(key, ignored -> new Stat()); stat.count.increment(); stat.totalLatency.add(Math.max(0, latencyMs));
        stat.requestBytes.add(Math.max(0, requestBytes)); stat.responseBytes.add(Math.max(0, responseBytes));
        if (contentType != null && !contentType.isBlank()) stat.contentType = contentType.substring(0, Math.min(128, contentType.length()));
        int family = Math.max(0, Math.min(5, status / 100)); stat.statuses[family].increment();
        int latencyBucket = latencyMs <= 10 ? 0 : latencyMs <= 50 ? 1 : latencyMs <= 100 ? 2 : latencyMs <= 500 ? 3 : latencyMs <= 1000 ? 4 : 5;
        stat.latency[latencyBucket].increment();
        if (status == 401 || status == 403) stat.denied.increment(); if (!authenticated) stat.unauthenticated.increment();
    }
    synchronized void flush() {
        if (values.isEmpty()) return; long end = System.currentTimeMillis(); List<Map<String, Object>> observations = new ArrayList<>();
        values.forEach((assetId, stat) -> {
            long count = stat.count.sum(); Map<String, Object> value = new LinkedHashMap<>(); value.put("assetId", assetId); value.put("count", count);
            value.put("status2xx", stat.statuses[2].sum()); value.put("status4xx", stat.statuses[4].sum()); value.put("status5xx", stat.statuses[5].sum());
            value.put("denied", stat.denied.sum()); value.put("unauthenticated", stat.unauthenticated.sum()); value.put("averageLatencyMs", count == 0 ? 0 : stat.totalLatency.sum() / count); observations.add(value);
            value.put("requestBytes", stat.requestBytes.sum()); value.put("responseBytes", stat.responseBytes.sum());
            value.put("contentType", stat.contentType);
            value.put("latencyBuckets", List.of(stat.latency[0].sum(), stat.latency[1].sum(), stat.latency[2].sum(), stat.latency[3].sum(), stat.latency[4].sum(), stat.latency[5].sum()));
        });
        values.clear();
        for (int index = 0; index < observations.size(); index += 100) {
            AgentBridge.emit(BridgeEvent.of("ApiObservationData", Map.of("schemaVersion", 1, "windowStart", windowStart,
                    "windowEnd", end, "observations", observations.subList(index, Math.min(index + 100, observations.size())))));
        }
        if (observations.stream().anyMatch(value -> "overflow".equals(value.get("assetId")))) {
            AgentBridge.emit(BridgeEvent.of("AgentHealthData", Map.of("schemaVersion", 1, "status", "degraded",
                    "capabilityMode", "api-cardinality-overflow", "queued", 0, "deadLetters", 0,
                    "accepted", 0, "rejected", 0, "failures", 0, "dropped", 0)));
        }
        windowStart = end;
    }
    @Override public void close() { flush(); executor.shutdown(); }
}
