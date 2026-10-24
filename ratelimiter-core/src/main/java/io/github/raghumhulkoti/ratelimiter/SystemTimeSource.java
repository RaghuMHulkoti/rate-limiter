package io.github.raghumhulkoti.ratelimiter;

public final class SystemTimeSource implements TimeSource {

    @Override
    public long currentTimeNanos() {
        return System.nanoTime();
    }
}
