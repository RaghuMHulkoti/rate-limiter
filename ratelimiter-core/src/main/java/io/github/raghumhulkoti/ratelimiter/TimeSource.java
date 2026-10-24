package io.github.raghumhulkoti.ratelimiter;

public interface TimeSource {

    long nowNanos();
}
