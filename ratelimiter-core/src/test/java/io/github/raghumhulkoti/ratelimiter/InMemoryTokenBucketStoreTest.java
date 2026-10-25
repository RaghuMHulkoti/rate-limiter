package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryTokenBucketStoreTest {

    private final InMemoryTokenBucketStore store = new InMemoryTokenBucketStore();

    @Test
    void concurrentRequestsOnOneKeyNeverExceedCapacity() throws Exception {
        Rule rule = Rule.tokenBucket("test", 10, 1, Duration.ofHours(1));
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
                    return store.evaluate("hot", rule, 1, 0).toCompletableFuture().join().allowed();
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

    @Test
    void keysHaveIndependentBuckets() {
        Rule rule = Rule.tokenBucket("test", 1, 1, Duration.ofSeconds(1));

        assertThat(evaluate("a", rule).allowed()).isTrue();
        assertThat(evaluate("a", rule).allowed()).isFalse();
        assertThat(evaluate("b", rule).allowed()).isTrue();
    }

    @Test
    void rejectsNonPositivePermits() {
        Rule rule = Rule.tokenBucket("test", 1, 1, Duration.ofSeconds(1));

        assertThatThrownBy(() -> store.evaluate("a", rule, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.evaluate("a", rule, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsTokenBucketOnly() {
        assertThat(store.supportedAlgorithms()).containsExactly(Algorithm.TOKEN_BUCKET);
    }

    private Decision evaluate(String key, Rule rule) {
        return store.evaluate(key, rule, 1, 0).toCompletableFuture().join();
    }
}
