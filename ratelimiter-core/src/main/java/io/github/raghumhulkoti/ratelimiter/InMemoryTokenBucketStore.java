package io.github.raghumhulkoti.ratelimiter;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryTokenBucketStore implements RateLimitStore {

    private final ConcurrentHashMap<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Decision> evaluate(String key, Rule rule, long permits, long currentTimeNanos) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(rule, "rule");
        if (rule.algorithm() != Algorithm.TOKEN_BUCKET) {
            throw new IllegalArgumentException("unsupported algorithm " + rule.algorithm());
        }
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0, was " + permits);
        }

        AtomicReference<BucketState> ref = buckets.computeIfAbsent(key, k -> new AtomicReference<>());
        while (true) {
            BucketState current = ref.get();
            TokenBucket.Result result = TokenBucket.compute(current, rule, permits, currentTimeNanos);
            if (result.newState() == current || ref.compareAndSet(current, result.newState())) {
                return CompletableFuture.completedFuture(result.decision());
            }
        }
    }

    @Override
    public Set<Algorithm> supportedAlgorithms() {
        return Set.of(Algorithm.TOKEN_BUCKET);
    }
}
