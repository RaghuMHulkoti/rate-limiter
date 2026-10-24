package io.github.raghumhulkoti.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RuleTest {

    @Test
    void storesValuesInNanos() {
        Rule rule = Rule.tokenBucket(100, 10, Duration.ofSeconds(1));

        assertThat(rule.algorithm()).isEqualTo(Algorithm.TOKEN_BUCKET);
        assertThat(rule.capacity()).isEqualTo(100);
        assertThat(rule.tokensPerRefill()).isEqualTo(10);
        assertThat(rule.refillIntervalNanos()).isEqualTo(1_000_000_000L);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> Rule.tokenBucket(0, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
        assertThatThrownBy(() -> Rule.tokenBucket(-1, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRefillTokens() {
        assertThatThrownBy(() -> Rule.tokenBucket(10, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokensPerRefill");
    }

    @Test
    void rejectsNonPositiveRefillPeriod() {
        assertThatThrownBy(() -> Rule.tokenBucket(10, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillInterval");
        assertThatThrownBy(() -> Rule.tokenBucket(10, 1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRefillPeriod() {
        assertThatThrownBy(() -> Rule.tokenBucket(10, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalByValue() {
        assertThat(Rule.tokenBucket(4, 1, Duration.ofSeconds(1)))
                .isEqualTo(Rule.tokenBucket(4, 1, Duration.ofMillis(1000)))
                .hasSameHashCodeAs(Rule.tokenBucket(4, 1, Duration.ofMillis(1000)))
                .isNotEqualTo(Rule.tokenBucket(5, 1, Duration.ofSeconds(1)));
    }
}
