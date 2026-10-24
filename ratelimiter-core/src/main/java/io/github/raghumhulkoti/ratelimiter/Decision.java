package io.github.raghumhulkoti.ratelimiter;

import java.util.Objects;

public final class Decision {

    public static final long NEVER = Long.MAX_VALUE;

    private final boolean allowed;
    private final long remaining;
    private final long limit;
    private final long retryAfterNanos;
    private final long timeUntilFullNanos;

    public Decision(boolean allowed, long remaining, long limit, long retryAfterNanos, long timeUntilFullNanos) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.limit = limit;
        this.retryAfterNanos = retryAfterNanos;
        this.timeUntilFullNanos = timeUntilFullNanos;
    }

    public boolean allowed() {
        return allowed;
    }

    public long remaining() {
        return remaining;
    }

    public long limit() {
        return limit;
    }

    public long retryAfterNanos() {
        return retryAfterNanos;
    }

    public long timeUntilFullNanos() {
        return timeUntilFullNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Decision other)) {
            return false;
        }
        return allowed == other.allowed
                && remaining == other.remaining
                && limit == other.limit
                && retryAfterNanos == other.retryAfterNanos
                && timeUntilFullNanos == other.timeUntilFullNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, remaining, limit, retryAfterNanos, timeUntilFullNanos);
    }

    @Override
    public String toString() {
        return "Decision[allowed=" + allowed
                + ", remaining=" + remaining
                + ", limit=" + limit
                + ", retryAfterNanos=" + retryAfterNanos
                + ", timeUntilFullNanos=" + timeUntilFullNanos + "]";
    }
}
