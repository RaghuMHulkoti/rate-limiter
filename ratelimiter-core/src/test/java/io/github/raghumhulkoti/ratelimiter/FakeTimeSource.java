package io.github.raghumhulkoti.ratelimiter;

import java.time.Duration;

final class FakeTimeSource implements TimeSource {

    private volatile long nowNanos;

    @Override
    public long nowNanos() {
        return nowNanos;
    }

    void set(Duration sinceStart) {
        nowNanos = sinceStart.toNanos();
    }

    void advance(Duration d) {
        nowNanos += d.toNanos();
    }
}
