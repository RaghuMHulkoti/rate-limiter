package io.github.raghumhulkoti.ratelimiter;

import java.time.Duration;
import java.util.Objects;

public final class Rule {

    private final String name;
    private final Algorithm algorithm;
    private final long capacity;
    private final long tokensPerRefill;
    private final long refillIntervalNanos;

    private Rule(String name, Algorithm algorithm, long capacity, long tokensPerRefill, long refillIntervalNanos) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (name.indexOf(':') >= 0) {
            throw new IllegalArgumentException("name must not contain ':', was " + name);
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, was " + capacity);
        }
        if (tokensPerRefill <= 0) {
            throw new IllegalArgumentException("tokensPerRefill must be > 0, was " + tokensPerRefill);
        }
        if (refillIntervalNanos <= 0) {
            throw new IllegalArgumentException("refillInterval must be > 0, was " + refillIntervalNanos + "ns");
        }
        this.name = name;
        this.algorithm = algorithm;
        this.capacity = capacity;
        this.tokensPerRefill = tokensPerRefill;
        this.refillIntervalNanos = refillIntervalNanos;
    }

    public static Rule tokenBucket(String name, long capacity, long tokensPerRefill, Duration refillInterval) {
        if (refillInterval == null) {
            throw new IllegalArgumentException("refillInterval must not be null");
        }
        return new Rule(name, Algorithm.TOKEN_BUCKET, capacity, tokensPerRefill, refillInterval.toNanos());
    }

    public String name() {
        return name;
    }

    public Algorithm algorithm() {
        return algorithm;
    }

    public long capacity() {
        return capacity;
    }

    public long tokensPerRefill() {
        return tokensPerRefill;
    }

    public long refillIntervalNanos() {
        return refillIntervalNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rule other)) {
            return false;
        }
        return name.equals(other.name)
                && algorithm == other.algorithm
                && capacity == other.capacity
                && tokensPerRefill == other.tokensPerRefill
                && refillIntervalNanos == other.refillIntervalNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, algorithm, capacity, tokensPerRefill, refillIntervalNanos);
    }

    @Override
    public String toString() {
        return "Rule[name=" + name
                + ", algorithm=" + algorithm
                + ", capacity=" + capacity
                + ", tokensPerRefill=" + tokensPerRefill
                + ", refillIntervalNanos=" + refillIntervalNanos + "]";
    }
}
