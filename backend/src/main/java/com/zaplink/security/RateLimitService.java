package com.zaplink.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitService {

    // Roadmap: switch to Caffeine cache with TTL eviction once user count grows.
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${ratelimit.create-link.capacity:30}")
    private long createLinkCapacity;

    @Value("${ratelimit.create-link.refill-period-seconds:60}")
    private long createLinkRefillSeconds;

    @Value("${ratelimit.register.capacity:5}")
    private long registerCapacity;

    @Value("${ratelimit.register.refill-period-seconds:60}")
    private long registerRefillSeconds;

    @Value("${ratelimit.login.capacity:10}")
    private long loginCapacity;

    @Value("${ratelimit.login.refill-period-seconds:60}")
    private long loginRefillSeconds;

    @Value("${ratelimit.default.capacity:120}")
    private long defaultCapacity;

    @Value("${ratelimit.default.refill-period-seconds:60}")
    private long defaultRefillSeconds;

    public Bucket resolveBucket(String policy, String key) {
        return buckets.computeIfAbsent(policy + ":" + key, k -> newBucket(policy));
    }

    public boolean tryConsume(String policy, String key) {
        return resolveBucket(policy, key).tryConsume(1);
    }

    private Bucket newBucket(String policy) {
        long capacity;
        long refillSeconds;

        switch (policy) {
            case "create-link" -> { capacity = createLinkCapacity; refillSeconds = createLinkRefillSeconds; }
            case "register"    -> { capacity = registerCapacity;   refillSeconds = registerRefillSeconds; }
            case "login"       -> { capacity = loginCapacity;      refillSeconds = loginRefillSeconds; }
            default            -> { capacity = defaultCapacity;    refillSeconds = defaultRefillSeconds; }
        }

        // TODO Phase 8 polish: migrate to Bucket4j 8.x builder API; Bandwidth.classic + Refill.intervally are deprecated.
        Bandwidth bandwidth = Bandwidth.classic(capacity,
                Refill.intervally(capacity, Duration.ofSeconds(refillSeconds)));
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
