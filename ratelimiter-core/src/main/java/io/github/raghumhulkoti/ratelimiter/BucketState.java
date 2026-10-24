package io.github.raghumhulkoti.ratelimiter;

final class BucketState {

    private final double tokens;
    private final long lastRefillNanos;

    BucketState(double tokens, long lastRefillNanos) {
        this.tokens = tokens;
        this.lastRefillNanos = lastRefillNanos;
    }

    double tokens() {
        return tokens;
    }

    long lastRefillNanos() {
        return lastRefillNanos;
    }

    @Override
    public String toString() {
        return "BucketState[tokens=" + tokens + ", lastRefillNanos=" + lastRefillNanos + "]";
    }
}
