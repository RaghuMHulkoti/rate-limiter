package io.github.raghumhulkoti.ratelimiter;

import java.time.Duration;

final class FakeTimeSource implements TimeSource {

    private volatile long currentTimeNanos;

    @Override
    public long currentTimeNanos() {
        return currentTimeNanos;
    }

    void set(Duration sinceStart) {
        currentTimeNanos = sinceStart.toNanos();
    }

    void advance(Duration d) {
        currentTimeNanos += d.toNanos();
    }
}
