package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;
    private static final Rule FOUR_PER_SECOND_REFILL_ONE = Rule.tokenBucket(4, 1, Duration.ofSeconds(1));

    @Test
    void newKeyStartsFull() {
        TokenBucket.Result result = TokenBucket.compute(null, FOUR_PER_SECOND_REFILL_ONE, 1, 0);

        assertThat(result.decision()).isEqualTo(new Decision(true, 3, 4, 0, SECOND));
        assertThat(result.newState().tokens()).isEqualTo(3.0);
    }

    @Test
    void retryAfterIsTimeToEarnMissingTokens() {
        BucketState empty = new BucketState(0, 0);

        Decision one = TokenBucket.compute(empty, FOUR_PER_SECOND_REFILL_ONE, 1, 0).decision();
        Decision three = TokenBucket.compute(empty, FOUR_PER_SECOND_REFILL_ONE, 3, 0).decision();

        assertThat(one).isEqualTo(new Decision(false, 0, 4, SECOND, 4 * SECOND));
        assertThat(three.retryAfterNanos()).isEqualTo(3 * SECOND);
    }

    @Test
    void refillsProportionallyToElapsedTime() {
        Rule tenPerMinute = Rule.tokenBucket(10, 10, Duration.ofMinutes(1));
        BucketState empty = new BucketState(0, 0);

        assertThat(TokenBucket.compute(empty, tenPerMinute, 1, 5 * SECOND).decision().allowed()).isFalse();
        assertThat(TokenBucket.compute(empty, tenPerMinute, 1, 6 * SECOND).decision().allowed()).isTrue();
    }

    @Test
    void longIdleCapsAtCapacity() {
        BucketState empty = new BucketState(0, 0);

        TokenBucket.Result result = TokenBucket.compute(empty, FOUR_PER_SECOND_REFILL_ONE, 1, 3600 * SECOND);

        assertThat(result.newState().tokens()).isEqualTo(3.0);
        assertThat(result.decision().remaining()).isEqualTo(3);
    }

    @Test
    void clockGoingBackwardsAddsNoTokens() {
        BucketState empty = new BucketState(0, 10 * SECOND);

        TokenBucket.Result result = TokenBucket.compute(empty, FOUR_PER_SECOND_REFILL_ONE, 1, 5 * SECOND);

        assertThat(result.decision().allowed()).isFalse();
        assertThat(result.newState()).isSameAs(empty);
    }

    @Test
    void rejectionReturnsSameStateInstance() {
        BucketState low = new BucketState(0.5, 0);

        TokenBucket.Result result = TokenBucket.compute(low, FOUR_PER_SECOND_REFILL_ONE, 1, 0);

        assertThat(result.decision().allowed()).isFalse();
        assertThat(result.newState()).isSameAs(low);
    }

    @Test
    void moreThanCapacityIsNeverAllowed() {
        TokenBucket.Result result = TokenBucket.compute(null, FOUR_PER_SECOND_REFILL_ONE, 5, 0);

        assertThat(result.decision().allowed()).isFalse();
        assertThat(result.decision().retryAfterNanos()).isEqualTo(Decision.NEVER);
        assertThat(result.decision().remaining()).isEqualTo(4);
        assertThat(result.newState().tokens()).isEqualTo(4.0);
    }

    @Test
    void wholeBucketCanBeTakenAtOnce() {
        Decision decision = TokenBucket.compute(null, FOUR_PER_SECOND_REFILL_ONE, 4, 0).decision();

        assertThat(decision).isEqualTo(new Decision(true, 0, 4, 0, 4 * SECOND));
    }
}
