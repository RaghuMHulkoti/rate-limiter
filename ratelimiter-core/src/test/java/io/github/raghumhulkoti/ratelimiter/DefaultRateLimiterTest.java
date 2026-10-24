package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultRateLimiterTest {

    private final FakeTimeSource clock = new FakeTimeSource();
    private final RateLimiter limiter = new DefaultRateLimiter(
            Rule.tokenBucket(4, 1, Duration.ofSeconds(1)), new InMemoryTokenBucketStore(), clock);

    @Test
    void animationStreamAllowsNineAndRejectsFour() {
        long[] arrivalsMillis = {1000, 3200, 3400, 3600, 3800, 4100, 4300, 4500, 4700, 4900, 6500, 7500, 9000};
        List<Long> allowed = new ArrayList<>();
        List<Long> rejected = new ArrayList<>();

        for (long t : arrivalsMillis) {
            clock.set(Duration.ofMillis(t));
            (limiter.tryAcquire("user", 1).allowed() ? allowed : rejected).add(t);
        }

        assertThat(allowed).hasSize(9);
        assertThat(rejected).containsExactly(4100L, 4500L, 4700L, 4900L);
    }

    @Test
    void readsTimeFromTimeSource() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("user", 1);
        }
        assertThat(limiter.tryAcquire("user", 1).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));

        assertThat(limiter.tryAcquire("user", 1).allowed()).isTrue();
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> limiter.tryAcquire("user", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.tryAcquire("user", -5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.tryAcquire(null, 1)).isInstanceOf(NullPointerException.class);
    }
}
