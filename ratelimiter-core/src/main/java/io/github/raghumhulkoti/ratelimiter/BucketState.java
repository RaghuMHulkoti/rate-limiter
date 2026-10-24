package io.github.raghumhulkoti.ratelimiter;

final class BucketState {

    private final double tokens;
    private final long lastUpdatedNanos;

    BucketState(double tokens, long lastUpdatedNanos) {
        this.tokens = tokens;
        this.lastUpdatedNanos = lastUpdatedNanos;
    }

    double tokens() {
        return tokens;
    }

    long lastUpdatedNanos() {
        return lastUpdatedNanos;
    }

    @Override
    public String toString() {
        return "BucketState[tokens=" + tokens + ", lastUpdatedNanos=" + lastUpdatedNanos + "]";
    }
}
