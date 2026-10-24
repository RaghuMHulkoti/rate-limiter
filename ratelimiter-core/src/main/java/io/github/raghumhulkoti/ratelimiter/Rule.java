package io.github.raghumhulkoti.ratelimiter;

import java.time.Duration;
import java.util.Objects;

public final class Rule {

    private final Algorithm algorithm;
    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;

    private Rule(Algorithm algorithm, long capacity, long refillTokens, long refillPeriodNanos) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, was " + capacity);
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be > 0, was " + refillTokens);
        }
        if (refillPeriodNanos <= 0) {
            throw new IllegalArgumentException("refillPeriod must be > 0, was " + refillPeriodNanos + "ns");
        }
        this.algorithm = algorithm;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriodNanos;
    }

    public static Rule tokenBucket(long capacity, long refillTokens, Duration refillPeriod) {
        if (refillPeriod == null) {
            throw new IllegalArgumentException("refillPeriod must not be null");
        }
        return new Rule(Algorithm.TOKEN_BUCKET, capacity, refillTokens, refillPeriod.toNanos());
    }

    public Algorithm algorithm() {
        return algorithm;
    }

    public long capacity() {
        return capacity;
    }

    public long refillTokens() {
        return refillTokens;
    }

    public long refillPeriodNanos() {
        return refillPeriodNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rule other)) {
            return false;
        }
        return algorithm == other.algorithm
                && capacity == other.capacity
                && refillTokens == other.refillTokens
                && refillPeriodNanos == other.refillPeriodNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, capacity, refillTokens, refillPeriodNanos);
    }

    @Override
    public String toString() {
        return "Rule[algorithm=" + algorithm
                + ", capacity=" + capacity
                + ", refillTokens=" + refillTokens
                + ", refillPeriodNanos=" + refillPeriodNanos + "]";
    }
}
