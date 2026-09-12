package com.coolxer.service.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {
    @Test
    void storesAndRestoresCompletedPayload() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        AtomicReference<String> stored = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            if (stored.get() != null) return false;
            stored.set(invocation.getArgument(1));
            return true;
        });
        when(values.get(anyString())).thenAnswer(ignored -> stored.get());
        doAnswer(invocation -> { stored.set(invocation.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));

        RedisIdempotencyStore store = new RedisIdempotencyStore(redis, Duration.ofMinutes(5), Duration.ofDays(14));
        assertEquals(ClaimStatus.ACQUIRED, store.claim("start"));
        store.complete("start", "{\"status\":1}");
        assertEquals(ClaimStatus.DONE, store.claim("start"));
        assertEquals("{\"status\":1}", store.completedPayload("start").orElseThrow());
    }
}
