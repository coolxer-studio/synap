package com.coolxer.service.sequence;

import com.coolxer.model.fact.Common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "synap.sequence.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryMessageSequenceStore implements MessageSequenceStore {
    private final Map<String, Entry> sequences = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;

    public InMemoryMessageSequenceStore(@Value("${synap.sequence.ttl:PT30M}") Duration ttl,
                                        @Value("${synap.sequence.memory.max-entries:100000}") int maxEntries) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized SequenceObservation observe(Common common, long sequence) {
        long now = System.currentTimeMillis();
        evict(now);
        String key = key(common);
        Entry entry = sequences.get(key);
        long previous = entry == null ? 0 : entry.maxSequence;
        String reason = sequence <= previous ? "SEQUENCE_REORDERED"
                : sequence > previous + 1 ? "SEQUENCE_GAP" : "SEQUENCE_OK";
        if (entry == null) {
            ensureCapacity();
            sequences.put(key, new Entry(sequence, now));
        } else {
            if (sequence > previous) entry.maxSequence = sequence;
            entry.updatedAt = now;
        }
        return new SequenceObservation(reason, previous, sequence);
    }

    private void ensureCapacity() {
        if (sequences.size() < maxEntries) return;
        sequences.entrySet().stream().min(Comparator.comparingLong(value -> value.getValue().updatedAt))
                .ifPresent(value -> sequences.remove(value.getKey(), value.getValue()));
    }

    private void evict(long now) {
        long expiredBefore = now - ttl.toMillis();
        sequences.entrySet().removeIf(value -> value.getValue().updatedAt <= expiredBefore);
    }

    private static String key(Common common) {
        return common.getAppId() + ":" + common.getStartId() + ":" + String.valueOf(common.getGuid());
    }

    private static final class Entry {
        private long maxSequence;
        private long updatedAt;
        private Entry(long maxSequence, long updatedAt) {
            this.maxSequence = maxSequence;
            this.updatedAt = updatedAt;
        }
    }
}
