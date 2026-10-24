package io.github.raghumhulkoti.ratelimiter;

public interface RateLimiter {

    Decision tryAcquire(String key, long permits);
}
