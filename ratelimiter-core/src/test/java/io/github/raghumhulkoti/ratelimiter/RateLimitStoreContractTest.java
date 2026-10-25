package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class RateLimitStoreContractTest {

    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    private RateLimitStore store;
    // Unique per test so stores backed by shared state (Redis) never see leftovers from another test.
    private String key;

    abstract RateLimitStore createStore();

    @BeforeEach
    void setUp() {
        store = createStore();
        key = UUID.randomUUID().toString();
    }

    @Test
    void supportsTokenBucket() {
        assertThat(store.supportedAlgorithms()).contains(Algorithm.TOKEN_BUCKET);
    }

    @Test
    void allowsUpToCapacityThenRejects() {
        Rule rule = rule(3, 1, Duration.ofSeconds(1));

        assertThat(evaluate(rule, 1, 0).allowed()).isTrue();
        assertThat(evaluate(rule, 1, 0).allowed()).isTrue();
        assertThat(evaluate(rule, 1, 0).allowed()).isTrue();
        assertThat(evaluate(rule, 1, 0).allowed()).isFalse();
    }

    @Test
    void reportsRemainingAndLimit() {
        Rule rule = rule(5, 1, Duration.ofSeconds(1));

        Decision first = evaluate(rule, 2, 0);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(3);
        assertThat(first.limit()).isEqualTo(5);
        assertThat(first.retryAfterNanos()).isZero();
        assertThat(first.timeUntilFullNanos()).isEqualTo(2 * SECOND);
    }

    @Test
    void rejectionReportsWhenToRetry() {
        Rule rule = rule(2, 1, Duration.ofSeconds(1));
        evaluate(rule, 2, 0);

        Decision rejected = evaluate(rule, 1, 0);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterNanos()).isEqualTo(SECOND);
        assertThat(rejected.timeUntilFullNanos()).isEqualTo(2 * SECOND);
    }

    @Test
    void refillsOverTime() {
        Rule rule = rule(1, 1, Duration.ofSeconds(1));
        assertThat(evaluate(rule, 1, 0).allowed()).isTrue();
        assertThat(evaluate(rule, 1, 0).allowed()).isFalse();

        assertThat(evaluate(rule, 1, SECOND).allowed()).isTrue();
    }

    @Test
    void refillNeverExceedsCapacity() {
        Rule rule = rule(3, 1, Duration.ofSeconds(1));
        evaluate(rule, 3, 0);
        long muchLater = 1_000 * SECOND;

        assertThat(evaluate(rule, 1, muchLater).allowed()).isTrue();
        assertThat(evaluate(rule, 1, muchLater).allowed()).isTrue();
        assertThat(evaluate(rule, 1, muchLater).allowed()).isTrue();
        assertThat(evaluate(rule, 1, muchLater).allowed()).isFalse();
    }

    @Test
    void rejectionDoesNotConsumeTokens() {
        Rule rule = rule(3, 1, Duration.ofHours(1));
        evaluate(rule, 2, 0);

        assertThat(evaluate(rule, 2, 0).allowed()).isFalse();
        assertThat(evaluate(rule, 1, 0).allowed()).isTrue();
    }

    @Test
    void rejectionDoesNotLoseAccruedRefill() {
        Rule rule = rule(1, 1, Duration.ofSeconds(1));
        evaluate(rule, 1, 0);

        assertThat(evaluate(rule, 1, SECOND / 2).allowed()).isFalse();
        // Half a token accrued by the rejected call must still count: 0.5s + 0.5s = one full token.
        assertThat(evaluate(rule, 1, SECOND).allowed()).isTrue();
    }

    @Test
    void permitsAboveCapacityCanNeverSucceed() {
        Rule rule = rule(3, 1, Duration.ofSeconds(1));

        Decision decision = evaluate(rule, 4, 0);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterNanos()).isEqualTo(Decision.NEVER);
    }

    @Test
    void keysHaveIndependentBuckets() {
        Rule rule = rule(1, 1, Duration.ofSeconds(1));
        String other = key + "-other";

        assertThat(store.evaluate(key, rule, 1, 0).toCompletableFuture().join().allowed()).isTrue();
        assertThat(store.evaluate(key, rule, 1, 0).toCompletableFuture().join().allowed()).isFalse();
        assertThat(store.evaluate(other, rule, 1, 0).toCompletableFuture().join().allowed()).isTrue();
    }

    @Test
    void rejectsNonPositivePermits() {
        Rule rule = rule(1, 1, Duration.ofSeconds(1));

        assertThat(failureOf(rule, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(failureOf(rule, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentRequestsOnOneKeyNeverExceedCapacity() throws Exception {
        Rule rule = rule(10, 1, Duration.ofHours(1));
        int threads = 100;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return evaluate(rule, 1, 0).allowed();
                }));
            }
            ready.await();
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> f : results) {
                if (f.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Rule rule(long capacity, long tokensPerRefill, Duration refillInterval) {
        return Rule.tokenBucket("contract", capacity, tokensPerRefill, refillInterval);
    }

    private Decision evaluate(Rule rule, long permits, long nowNanos) {
        return store.evaluate(key, rule, permits, nowNanos).toCompletableFuture().join();
    }

    // A store may throw directly or fail the returned stage; both count as rejecting the call.
    private Throwable failureOf(Rule rule, long permits) {
        Throwable thrown = catchThrowable(() -> {
            CompletionStage<Decision> stage = store.evaluate(key, rule, permits, 0);
            stage.toCompletableFuture().join();
        });
        assertThat(thrown).isNotNull();
        return thrown instanceof CompletionException && thrown.getCause() != null ? thrown.getCause() : thrown;
    }
}
