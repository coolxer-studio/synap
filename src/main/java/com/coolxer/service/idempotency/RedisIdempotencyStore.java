package com.coolxer.service.idempotency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
@ConditionalOnProperty(name = "synap.idempotency.store", havingValue = "redis")
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final String PROCESSING = "processing";
    private static final String DONE = "done";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration processingTtl;
    private final Duration doneTtl;

    public RedisIdempotencyStore(StringRedisTemplate redis,
            @Value("${synap.idempotency.processing-ttl:PT5M}") Duration processingTtl,
            @Value("${synap.idempotency.done-ttl:P14D}") Duration doneTtl) {
        this.redis = redis;
        this.processingTtl = processingTtl;
        this.doneTtl = doneTtl;
    }

    @Override
    public ClaimStatus claim(String key) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, PROCESSING, processingTtl);
        if (Boolean.TRUE.equals(acquired)) return ClaimStatus.ACQUIRED;
        return DONE.equals(redis.opsForValue().get(key)) ? ClaimStatus.DONE : ClaimStatus.PROCESSING;
    }

    @Override
    public void complete(String key) {
        redis.opsForValue().set(key, DONE, doneTtl);
    }

    @Override
    public void release(String key) {
        redis.execute(RELEASE_SCRIPT, Collections.singletonList(key), PROCESSING);
    }
}
