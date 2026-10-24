package io.github.raghumhulkoti.ratelimiter;

import java.util.Objects;

public final class DefaultRateLimiter implements RateLimiter {

    private final Rule rule;
    private final RateLimitStore store;
    private final TimeSource timeSource;

    public DefaultRateLimiter(Rule rule, RateLimitStore store, TimeSource timeSource) {
        this.rule = Objects.requireNonNull(rule, "rule");
        this.store = Objects.requireNonNull(store, "store");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        if (!store.supportedAlgorithms().contains(rule.algorithm())) {
            throw new IllegalArgumentException("store does not support " + rule.algorithm());
        }
    }

    @Override
    public Decision tryAcquire(String key, long permits) {
        Objects.requireNonNull(key, "key");
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0, was " + permits);
        }
        long now = timeSource.currentTimeNanos();
        return store.evaluate(key, rule, permits, now).toCompletableFuture().join();
    }
}
