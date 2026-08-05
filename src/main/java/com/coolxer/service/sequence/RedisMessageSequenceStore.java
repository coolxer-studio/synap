package com.coolxer.service.sequence;

import com.coolxer.model.fact.Common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
@ConditionalOnProperty(name = "synap.sequence.store", havingValue = "redis")
public class RedisMessageSequenceStore implements MessageSequenceStore {
    private static final DefaultRedisScript<String> OBSERVE = new DefaultRedisScript<>(
            "local current=redis.call('get',KEYS[1]); local incoming=tonumber(ARGV[1]); local ttl=tonumber(ARGV[2]); " +
            "if not current then redis.call('psetex',KEYS[1],ttl,incoming); " +
            "if incoming>1 then return 'SEQUENCE_GAP:0' else return 'SEQUENCE_OK:0' end end; " +
            "local previous=tonumber(current); redis.call('pexpire',KEYS[1],ttl); " +
            "if incoming<=previous then return 'SEQUENCE_REORDERED:'..previous end; " +
            "redis.call('set',KEYS[1],incoming,'PX',ttl); " +
            "if incoming>previous+1 then return 'SEQUENCE_GAP:'..previous end; return 'SEQUENCE_OK:'..previous",
            String.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisMessageSequenceStore(StringRedisTemplate redis,
                                     @Value("${synap.sequence.ttl:PT30M}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public SequenceObservation observe(Common common, long sequence) {
        String result = redis.execute(OBSERVE, Collections.singletonList(key(common)),
                String.valueOf(sequence), String.valueOf(Math.max(1000, ttl.toMillis())));
        if (result == null) return new SequenceObservation("SEQUENCE_STATE_UNAVAILABLE", 0, sequence);
        String[] values = result.split(":", 2);
        long previous;
        try { previous = values.length == 2 ? Long.parseLong(values[1]) : 0; }
        catch (NumberFormatException ignored) { previous = 0; }
        return new SequenceObservation(values[0], previous, sequence);
    }

    private static String key(Common common) {
        return "synap:h5:sequence:" + common.getAppId() + ":" + common.getStartId() + ":" + String.valueOf(common.getGuid());
    }
}
