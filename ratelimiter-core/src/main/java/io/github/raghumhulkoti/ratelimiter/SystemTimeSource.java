package io.github.raghumhulkoti.ratelimiter;

public final class SystemTimeSource implements TimeSource {

    @Override
    public long nowNanos() {
        return System.nanoTime();
    }
}
